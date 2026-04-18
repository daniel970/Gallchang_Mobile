package com.dcinside.crawler.mobile

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.dcinside.crawler.mobile.databinding.ActivityMainBinding
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.time.LocalDate
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val adapter = UserRankAdapter()

    private var rangeMode: RangeMode = RangeMode.PAGES
    private var rankingMode: RankingMode = RankingMode.POSTS
    private var startDate: LocalDate? = null
    private var endDate: LocalDate? = null

    // 결과 페이지네이션 상태
    private var allItems: List<UserRankItem> = emptyList()
    private var currentPage: Int = 0

    private enum class RangeMode { PAGES, DATES }

    companion object {
        private const val PAGE_SIZE = 50

        /** 디시 갤 ID 허용 문자: 영문/숫자/언더스코어. URL/특수문자 입력 차단용. */
        private val BOARD_ID_PATTERN = Regex("^[A-Za-z0-9_]+$")
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* 사용자가 거부해도 크롤 자체는 계속 진행됨. 알림만 뜨지 않을 뿐. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        binding.recyclerRanks.layoutManager = LinearLayoutManager(this)
        binding.recyclerRanks.adapter = adapter

        binding.toggleRange.addOnButtonCheckedListener(
            MaterialButtonToggleGroup.OnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@OnButtonCheckedListener
                rangeMode = when (checkedId) {
                    R.id.btnRangeDates -> RangeMode.DATES
                    else -> RangeMode.PAGES
                }
                refreshRangeVisibility()
            },
        )

        binding.toggleRanking.addOnButtonCheckedListener(
            MaterialButtonToggleGroup.OnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@OnButtonCheckedListener
                rankingMode = when (checkedId) {
                    R.id.btnRankComments -> RankingMode.COMMENTS
                    else -> RankingMode.POSTS
                }
                binding.textRankingNote.visibility =
                    if (rankingMode == RankingMode.COMMENTS) View.VISIBLE else View.GONE
            },
        )

        binding.editStartDate.setOnClickListener { pickDate(isStart = true) }
        binding.editEndDate.setOnClickListener { pickDate(isStart = false) }

        binding.btnStart.setOnClickListener { onStartClicked() }
        binding.btnStop.setOnClickListener { onStopClicked() }

        binding.btnPrevPage.setOnClickListener {
            if (currentPage > 0) {
                currentPage--
                refreshResults()
            }
        }
        binding.btnNextPage.setOnClickListener {
            val filteredSize = currentFilteredSize()
            val totalPages = totalPagesFor(filteredSize)
            if (currentPage + 1 < totalPages) {
                currentPage++
                refreshResults()
            }
        }

        binding.editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                currentPage = 0
                refreshResults()
            }
        })

        refreshRangeVisibility()

        // 진행 이벤트 및 상태 스냅샷 구독
        CrawlerController.events
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { handleProgress(it) }
            .launchIn(lifecycleScope)

        CrawlerController.state
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { restoreFromSnapshot(it) }
            .launchIn(lifecycleScope)
    }

    private fun refreshRangeVisibility() {
        binding.groupPages.visibility =
            if (rangeMode == RangeMode.PAGES) View.VISIBLE else View.GONE
        binding.groupDates.visibility =
            if (rangeMode == RangeMode.DATES) View.VISIBLE else View.GONE
    }

    private fun pickDate(isStart: Boolean) {
        hideKeyboard()
        val today = LocalDate.now()
        val init = if (isStart) startDate ?: today.minusDays(7) else endDate ?: today
        val dialog = DatePickerDialog(
            this,
            { _, y, m, d ->
                val picked = LocalDate.of(y, m + 1, d)
                if (isStart) {
                    startDate = picked
                    binding.editStartDate.setText(formatDate(picked))
                } else {
                    endDate = picked
                    binding.editEndDate.setText(formatDate(picked))
                }
            },
            init.year,
            init.monthValue - 1,
            init.dayOfMonth,
        )
        dialog.show()
    }

    private fun formatDate(date: LocalDate): String {
        return getString(
            R.string.fmt_date_display,
            date.year,
            date.monthValue,
            date.dayOfMonth,
        )
    }

    private fun onStartClicked() {
        val boardId = binding.editGallery.text?.toString()?.trim().orEmpty()
        if (boardId.isBlank()) {
            toast(getString(R.string.msg_need_gallery))
            return
        }
        if (!BOARD_ID_PATTERN.matches(boardId)) {
            toast(getString(R.string.msg_invalid_gallery_id))
            return
        }

        val range: CrawlRange = when (rangeMode) {
            RangeMode.PAGES -> {
                val s = binding.editStartPage.text?.toString()?.trim()?.toIntOrNull() ?: 1
                val e = binding.editEndPage.text?.toString()?.trim()?.toIntOrNull() ?: 1
                if (s < 1 || e < s) {
                    toast(getString(R.string.msg_invalid_page))
                    return
                }
                CrawlRange.Pages(startPage = s, endPage = e)
            }
            RangeMode.DATES -> {
                val s = startDate
                val e = endDate
                if (s == null || e == null) {
                    toast(getString(R.string.msg_need_dates))
                    return
                }
                if (s.isAfter(e)) {
                    toast(getString(R.string.msg_invalid_date))
                    return
                }
                CrawlRange.Dates(startDate = s, endDate = e)
            }
        }

        hideKeyboard()
        setAllItems(emptyList())
        binding.editSearch.setText("")
        binding.textSummary.text = ""
        binding.textLastLog.text = getString(R.string.status_running)
        setRunningUi(true)

        ensureNotificationPermission()

        CrawlerController.start(
            appContext = applicationContext,
            request = CrawlRequest(
                boardId = boardId,
                range = range,
                rankingMode = rankingMode,
            ),
            rangeDesc = rangeDescFor(range),
        )
    }

    private fun rangeDescFor(range: CrawlRange): String = when (range) {
        is CrawlRange.Pages -> getString(R.string.fmt_range_pages, range.startPage, range.endPage)
        is CrawlRange.Dates -> getString(
            R.string.fmt_range_dates,
            formatDate(range.startDate),
            formatDate(range.endDate),
        )
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_history -> {
                startActivity(Intent(this, HistoryActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun onStopClicked() {
        CrawlerController.cancel()
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun handleProgress(progress: CrawlProgress) {
        when (progress) {
            is CrawlProgress.Started -> {
                binding.progress.isIndeterminate = false
                binding.progress.max = 100
                binding.progress.progress = 0
                binding.progress.visibility = View.VISIBLE
            }
            is CrawlProgress.PageCompleted -> {
                binding.progress.isIndeterminate = false
                binding.progress.max = 100
                binding.progress.setProgressCompat(progress.percent, true)
                val total = progress.totalPages
                binding.textLastLog.text = if (total != null && total > 0) {
                    getString(
                        R.string.fmt_progress_page,
                        progress.page,
                        total,
                        progress.percent,
                        progress.cumulativeArticles,
                    )
                } else {
                    getString(
                        R.string.fmt_progress_page_unknown,
                        progress.page,
                        progress.percent,
                        progress.cumulativeArticles,
                    )
                }
            }
            is CrawlProgress.PageFailed -> {
                binding.textLastLog.text = getString(
                    R.string.fmt_page_failed,
                    progress.page,
                    progress.attempt,
                    progress.maxAttempts,
                    progress.error,
                )
            }
            is CrawlProgress.PageSkipped -> {
                binding.textLastLog.text = getString(
                    R.string.fmt_page_skipped,
                    progress.page,
                    progress.reason,
                )
            }
            is CrawlProgress.Finished -> {
                val items = UserRankMapper.toItems(progress.ranks, progress.rankingMode)
                setAllItems(items)
                binding.progress.setProgressCompat(100, true)
                binding.textSummary.text = getString(
                    R.string.fmt_summary,
                    progress.totalArticles,
                    progress.totalUsers,
                )
                binding.textLastLog.text = getString(R.string.status_done)
                binding.textStatus.text = getString(R.string.status_done)
                setRunningUi(false)
            }
            CrawlProgress.Cancelled -> {
                binding.textStatus.text = getString(R.string.status_cancelled)
                binding.textLastLog.text = getString(R.string.status_cancelled)
                setRunningUi(false)
            }
        }
    }

    /**
     * Activity 가 새로 만들어졌거나 백그라운드에서 돌아온 경우,
     * 컨트롤러가 들고 있던 최신 스냅샷으로 UI 를 한 번에 재구성한다.
     *
     * 진행 중이 아닐 때(초기 또는 완료 후) 에도 마지막 랭킹/로그가 남도록 한다.
     */
    private fun restoreFromSnapshot(snap: CrawlerSnapshot) {
        setRunningUi(snap.isRunning)
        if (snap.isRunning) {
            binding.progress.isIndeterminate = false
            binding.progress.max = 100
            binding.progress.setProgressCompat(snap.percent, false)
            binding.progress.visibility = View.VISIBLE
            binding.textStatus.text = getString(R.string.status_running)
        } else if (snap.finishedOk) {
            binding.textStatus.text = getString(R.string.status_done)
        } else if (snap.cancelled) {
            binding.textStatus.text = getString(R.string.status_cancelled)
        }

        if (snap.lastLog.isNotBlank()) {
            binding.textLastLog.text = snap.lastLog
        }
        if (snap.finishedOk) {
            binding.textSummary.text = getString(
                R.string.fmt_summary,
                snap.totalArticles,
                snap.totalUsers,
            )
        }
        if (snap.ranks.isNotEmpty() && allItems.isEmpty()) {
            setAllItems(UserRankMapper.toItems(snap.ranks, snap.rankingMode))
        }
    }

    private fun setRunningUi(running: Boolean) {
        binding.btnStart.isEnabled = !running
        binding.btnStop.isEnabled = running
        binding.progress.visibility = if (running) View.VISIBLE else View.GONE
        if (running) {
            binding.textStatus.text = getString(R.string.status_running)
        }
        val inputsEnabled = !running
        binding.editGallery.isEnabled = inputsEnabled
        binding.editStartPage.isEnabled = inputsEnabled
        binding.editEndPage.isEnabled = inputsEnabled
        binding.editStartDate.isEnabled = inputsEnabled
        binding.editEndDate.isEnabled = inputsEnabled
        for (i in 0 until binding.toggleRange.childCount) {
            binding.toggleRange.getChildAt(i).isEnabled = inputsEnabled
        }
        for (i in 0 until binding.toggleRanking.childCount) {
            binding.toggleRanking.getChildAt(i).isEnabled = inputsEnabled
        }
    }

    /**
     * 누적된 결과 리스트를 교체하고 검색/페이지 상태를 1페이지로 리셋한다.
     */
    private fun setAllItems(items: List<UserRankItem>) {
        allItems = items
        currentPage = 0
        refreshResults()
    }

    /**
     * 현재 검색어와 페이지에 맞춰 어댑터 내용과 페이지네이션 UI 를 동기화한다.
     */
    private fun refreshResults() {
        val query = binding.editSearch.text?.toString()?.trim()?.lowercase(Locale.getDefault()).orEmpty()
        val filtered = if (query.isEmpty()) {
            allItems
        } else {
            allItems.filter { it.nick.lowercase(Locale.getDefault()).contains(query) }
        }

        val totalPages = totalPagesFor(filtered.size)
        if (currentPage >= totalPages) currentPage = (totalPages - 1).coerceAtLeast(0)

        val pageSlice = if (filtered.isEmpty()) {
            emptyList()
        } else {
            val from = currentPage * PAGE_SIZE
            val to = minOf(from + PAGE_SIZE, filtered.size)
            filtered.subList(from, to)
        }
        adapter.submit(pageSlice)

        // 검색 바 / 결과 수: 한 번이라도 수집된 결과가 있을 때만 노출
        val hasAny = allItems.isNotEmpty()
        binding.layoutSearch.visibility = if (hasAny) View.VISIBLE else View.GONE
        binding.textResultCount.text = getString(R.string.fmt_result_count, filtered.size)

        // 페이지네이션 바: 2페이지 이상일 때만
        binding.layoutPagination.visibility = if (totalPages > 1) View.VISIBLE else View.GONE
        binding.textPageInfo.text = if (totalPages > 0) {
            getString(R.string.fmt_page_info, currentPage + 1, totalPages)
        } else {
            ""
        }
        binding.btnPrevPage.isEnabled = currentPage > 0
        binding.btnNextPage.isEnabled = currentPage + 1 < totalPages

        // Empty state: 아예 결과가 없으면 기본 문구, 검색 결과만 0이면 "검색 결과 없음"
        binding.textEmpty.visibility = if (pageSlice.isEmpty()) View.VISIBLE else View.GONE
        binding.textEmpty.text = when {
            !hasAny -> getString(R.string.empty_hint)
            filtered.isEmpty() -> getString(R.string.empty_search_hint)
            else -> ""
        }
    }

    private fun currentFilteredSize(): Int {
        val query = binding.editSearch.text?.toString()?.trim()?.lowercase(Locale.getDefault()).orEmpty()
        return if (query.isEmpty()) allItems.size
        else allItems.count { it.nick.lowercase(Locale.getDefault()).contains(query) }
    }

    private fun totalPagesFor(size: Int): Int =
        if (size <= 0) 0 else (size - 1) / PAGE_SIZE + 1

    private fun hideKeyboard() {
        val view = currentFocus ?: binding.root
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }
}
