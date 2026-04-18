package com.dcinside.crawler.mobile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.dcinside.crawler.mobile.databinding.ItemUserRankBinding

data class UserRankItem(
    val rank: Int,
    val displayRank: String,
    val nick: String,
    val idOrIp: String,
    val primaryValue: Int,
    val primaryLabelRes: Int, // R.string.fmt_post_count or fmt_comment_count
    val sharePercent: Double,
    val subStats: SubStats,
) {
    data class SubStats(
        val postCount: Int,
        val commentSum: Int,
        val viewSum: Int,
        val recommendSum: Int,
        val mode: RankingMode,
    )
}

class UserRankAdapter : RecyclerView.Adapter<UserRankAdapter.VH>() {

    private val items = mutableListOf<UserRankItem>()

    fun submit(newItems: List<UserRankItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemUserRankBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class VH(private val binding: ItemUserRankBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: UserRankItem) {
            val ctx = binding.root.context
            binding.textRank.text = item.displayRank
            binding.textNick.text = item.nick.ifBlank { "(닉 없음)" }
            binding.textIdOrIp.text = item.idOrIp
            binding.textPostCount.text = ctx.getString(item.primaryLabelRes, item.primaryValue)
            binding.textShare.text = ctx.getString(R.string.fmt_share, item.sharePercent)

            val s = item.subStats
            val subRes = when (s.mode) {
                RankingMode.POSTS -> R.string.fmt_sub_stats_posts
                RankingMode.COMMENTS -> R.string.fmt_sub_stats_comments
            }
            binding.textSubStats.text = ctx.getString(
                subRes,
                s.postCount,
                s.commentSum,
                s.viewSum,
                s.recommendSum,
            )
        }
    }
}

object UserRankMapper {
    fun toItems(ranks: List<UserRank>, mode: RankingMode): List<UserRankItem> {
        if (ranks.isEmpty()) return emptyList()

        val totalPrimary = when (mode) {
            RankingMode.POSTS -> ranks.sumOf { it.postCount }
            RankingMode.COMMENTS -> ranks.sumOf { it.commentSum }
        }
        val primaryLabelRes = when (mode) {
            RankingMode.POSTS -> R.string.fmt_post_count
            RankingMode.COMMENTS -> R.string.fmt_comment_count
        }

        val out = ArrayList<UserRankItem>(ranks.size)
        var displayRank = 0
        var previousValue = -1
        for ((index, r) in ranks.withIndex()) {
            val primary = when (mode) {
                RankingMode.POSTS -> r.postCount
                RankingMode.COMMENTS -> r.commentSum
            }
            if (index == 0 || primary != previousValue) {
                displayRank = index + 1
                previousValue = primary
            }
            val share = if (totalPrimary > 0) primary * 100.0 / totalPrimary else 0.0
            val idOrIp = r.uid.ifBlank { r.ip }
            out += UserRankItem(
                rank = displayRank,
                displayRank = "${displayRank}위",
                nick = r.nick,
                idOrIp = idOrIp,
                primaryValue = primary,
                primaryLabelRes = primaryLabelRes,
                sharePercent = share,
                subStats = UserRankItem.SubStats(
                    postCount = r.postCount,
                    commentSum = r.commentSum,
                    viewSum = r.viewSum,
                    recommendSum = r.recommendSum,
                    mode = mode,
                ),
            )
        }
        return out
    }
}
