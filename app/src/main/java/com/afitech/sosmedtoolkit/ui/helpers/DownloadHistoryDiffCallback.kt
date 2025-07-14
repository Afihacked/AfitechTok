package com.afitech.sosmedtoolkit.ui.helpers

import androidx.recyclerview.widget.DiffUtil
import com.afitech.sosmedtoolkit.data.model.DownloadHistory

class DownloadHistoryDiffCallback(
    private val oldList: List<DownloadHistory>,
    private val newList: List<DownloadHistory>
) : DiffUtil.Callback() {

    override fun getOldListSize(): Int = oldList.size
    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition].id == newList[newItemPosition].id
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition] == newList[newItemPosition]
    }
}
