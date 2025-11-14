package com.creativem.tvfullurl

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class VideoDownloadAdapter(
    private val videos: MutableList<VideoDownload>
) : RecyclerView.Adapter<VideoDownloadAdapter.VideoViewHolder>() {

    inner class VideoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtName: TextView = view.findViewById(R.id.txtName)
        val txtStatus: TextView = view.findViewById(R.id.txtStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_video_download, parent, false)
        return VideoViewHolder(view)
    }

    override fun getItemCount(): Int = videos.size

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val video = videos[position]
        holder.txtName.text = video.url
        holder.txtStatus.text = "${video.status} (${video.progress}%)"
    }

    fun addVideo(video: VideoDownload) {
        videos.add(video)
        notifyItemInserted(videos.size - 1)
    }

    fun updateProgress(url: String, progress: Int, status: String) {
        val index = videos.indexOfFirst { it.url == url }
        if (index != -1) {
            videos[index].progress = progress
            videos[index].status = status
            notifyItemChanged(index)
        }
    }
}
