package com.dcinside.crawler.mobile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.dcinside.crawler.mobile.databinding.ItemHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private val onClick: (CrawlHistoryMeta) -> Unit,
    private val onDelete: (CrawlHistoryMeta) -> Unit,
) : RecyclerView.Adapter<HistoryAdapter.VH>() {

    private val items = mutableListOf<CrawlHistoryMeta>()

    fun submit(newItems: List<CrawlHistoryMeta>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun removeById(id: String): Int {
        val idx = items.indexOfFirst { it.id == id }
        if (idx >= 0) {
            items.removeAt(idx)
            notifyItemRemoved(idx)
        }
        return items.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return VH(binding, onClick, onDelete)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    class VH(
        private val binding: ItemHistoryBinding,
        private val onClick: (CrawlHistoryMeta) -> Unit,
        private val onDelete: (CrawlHistoryMeta) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CrawlHistoryMeta) {
            val ctx = binding.root.context
            binding.textBoardId.text = item.boardId
            binding.textCreatedAt.text = DATE_FMT.format(Date(item.createdAt))
            val modeLabel = when (item.rankingMode) {
                RankingMode.POSTS -> ctx.getString(R.string.tab_rank_posts)
                RankingMode.COMMENTS -> ctx.getString(R.string.tab_rank_comments)
            }
            binding.textRangeDesc.text = ctx.getString(
                R.string.fmt_history_range,
                item.rangeDesc,
                modeLabel,
            )
            binding.textSummary.text = ctx.getString(
                R.string.fmt_summary,
                item.totalArticles,
                item.totalUsers,
            )
            binding.root.setOnClickListener { onClick(item) }
            binding.btnDelete.setOnClickListener { onDelete(item) }
            binding.root.setOnLongClickListener {
                onDelete(item)
                true
            }
        }

        companion object {
            private val DATE_FMT = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault())
        }
    }
}
