package com.dcinside.crawler.mobile

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.dcinside.crawler.mobile.databinding.ActivityHistoryDetailBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 과거 기록 하나를 상세 보기. MainActivity 와 동일한 검색 + 50개 페이지네이션 UX.
 */
class HistoryDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryDetailBinding
    private val adapter = UserRankAdapter()

    private var allItems: List<UserRankItem> = emptyList()
    private var currentPage: Int = 0

    companion object {
        const val EXTRA_ID = "history_id"
        private const val PAGE_SIZE = 50
        private val DATE_FMT = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.recyclerRanks.layoutManager = LinearLayoutManager(this)
        binding.recyclerRanks.adapter = adapter

        val id = intent.getStringExtra(EXTRA_ID)
        if (id.isNullOrBlank()) {
            finish()
            return
        }

        val entry = CrawlHistoryStore(this).load(id)
        if (entry == null) {
            finish()
            return
        }

        bindHeader(entry)

        allItems = UserRankMapper.toItems(entry.ranks, entry.rankingMode)
        currentPage = 0

        binding.editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                currentPage = 0
                refreshResults()
            }
        })

        binding.btnPrevPage.setOnClickListener {
            if (currentPage > 0) {
                currentPage--
                refreshResults()
            }
        }
        binding.btnNextPage.setOnClickListener {
            val size = currentFilteredSize()
            val total = totalPagesFor(size)
            if (currentPage + 1 < total) {
                currentPage++
                refreshResults()
            }
        }

        refreshResults()
    }

    private fun bindHeader(entry: CrawlHistoryEntry) {
        binding.toolbar.title = entry.boardId
        binding.textHeaderRange.text = entry.rangeDesc
        val modeLabel = when (entry.rankingMode) {
            RankingMode.POSTS -> getString(R.string.tab_rank_posts)
            RankingMode.COMMENTS -> getString(R.string.tab_rank_comments)
        }
        binding.textHeaderMode.text = getString(
            R.string.fmt_history_detail_mode,
            modeLabel,
            DATE_FMT.format(Date(entry.createdAt)),
        )
        binding.textHeaderSummary.text = getString(
            R.string.fmt_summary,
            entry.totalArticles,
            entry.totalUsers,
        )
    }

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

        binding.textResultCount.text = getString(R.string.fmt_result_count, filtered.size)
        binding.layoutPagination.visibility = if (totalPages > 1) View.VISIBLE else View.GONE
        binding.textPageInfo.text = if (totalPages > 0) {
            getString(R.string.fmt_page_info, currentPage + 1, totalPages)
        } else {
            ""
        }
        binding.btnPrevPage.isEnabled = currentPage > 0
        binding.btnNextPage.isEnabled = currentPage + 1 < totalPages

        binding.textEmpty.visibility = if (pageSlice.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun currentFilteredSize(): Int {
        val query = binding.editSearch.text?.toString()?.trim()?.lowercase(Locale.getDefault()).orEmpty()
        return if (query.isEmpty()) allItems.size
        else allItems.count { it.nick.lowercase(Locale.getDefault()).contains(query) }
    }

    private fun totalPagesFor(size: Int): Int =
        if (size <= 0) 0 else (size - 1) / PAGE_SIZE + 1
}
