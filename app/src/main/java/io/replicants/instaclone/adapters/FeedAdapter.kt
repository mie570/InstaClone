package io.replicants.instaclone.adapters

import android.app.Activity
import android.content.Context
import android.graphics.drawable.Drawable
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.text.bold
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import io.replicants.instaclone.R
import io.replicants.instaclone.maintabs.BaseMainFragment
import io.replicants.instaclone.network.InstaApi
import io.replicants.instaclone.network.InstaApiCallback
import io.replicants.instaclone.pojos.Photo
import io.replicants.instaclone.subfragments.UserListSubFragment
import io.replicants.instaclone.utilities.Utils
import io.replicants.instaclone.utilities.setClickableSpan
import kotlinx.android.synthetic.main.adapter_feed_item.view.*
import kotlinx.android.synthetic.main.adapter_feed_item_grid.view.*
import org.json.JSONObject
import java.util.*

class FeedAdapter(private val context: Activity, private val dataset: ArrayList<Photo?>, private val recyclerView: RecyclerView) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var onLoadMoreListener: OnLoadMoreListener? = null
    var currentlyLoading = false
    var canLoadMore = true
    var header = LayoutInflater.from(context).inflate(R.layout.feed_header_dummy, null, false)
    var clickListeners:BaseMainFragment.ClickListeners? = null

    private val visibleThreshold = 6
    private val VIEW_TYPE_HEADER = 0
    private val VIEW_TYPE_PHOTO = 1
    private val VIEW_TYPE_PHOTO_GRID = 2
    private val VIEW_TYPE_LOADING = 3


    // this inner class doesn't need to be static since we never use it outside of FeedAdapter
    private inner class PhotoViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        // each data item is just a string in this case
        var ivProfileHead = v.feed_item_profile_head
        var tvProfileName = v.feed_item_profile_name
        var tvLocationName = v.feed_item_location_name
        var tvDistance = v.feed_item_distance
        var ivPhoto = v.feed_item_image
        var progressBar = v.feed_item_loading
        var btLike = v.feed_item_like
        var btComment = v.feed_item_comment
        var tvLikeText = v.feed_item_like_text
        var tvCaption = v.feed_item_caption
        var tvCommentPreviews = v.feed_item_comment_text_previews
        var tvCommentText = v.feed_item_comment_text
        var tvDate = v.feed_item_date

        init {

            tvProfileName.movementMethod = LinkMovementMethod.getInstance()
            tvLocationName.movementMethod = LinkMovementMethod.getInstance()
            tvLikeText.movementMethod = LinkMovementMethod.getInstance()
            tvCaption.movementMethod = LinkMovementMethod.getInstance()
            tvCommentPreviews.movementMethod = LinkMovementMethod.getInstance()
            tvCommentText.movementMethod = LinkMovementMethod.getInstance()

            @Suppress
            ivPhoto.setOnTouchListener(object : View.OnTouchListener {
                private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        val item = dataset[adapterPosition-1]
                        handleLike(btLike, item!!)
                        return super.onDoubleTap(e)
                    }
                })

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    gestureDetector.onTouchEvent(event)
                    return true
                }
            })


        }
    }

    private inner class PhotoGridViewHolder(v: ImageView) : RecyclerView.ViewHolder(v) {
        var ivPhoto = v.feed_item_grid_image
        init{
            ivPhoto.setAspectRatio(1.0f)
        }
    }

    private inner class ProgressViewHolder(v: LinearLayout) : RecyclerView.ViewHolder(v)
    private inner class HeaderViewHolder(var container: FrameLayout) : RecyclerView.ViewHolder(container)

    private fun handleLike(button: Button, item: Photo) {
        toggleLike(button, item)

        if (item.isLiked) {
            InstaApi.likePhoto(item.photoID).enqueue(InstaApi.generateCallback(context, object : InstaApiCallback() {
                override fun success(jsonResponse: JSONObject) {
                    if (!jsonResponse.optBoolean("success")) {
                        toggleLike(button, item)
                    }
                }

                override fun failure(context: Context,jsonResponse: JSONObject?) = toggleLike(button, item)

            }))
        } else {
            InstaApi.unlikePhoto(item.photoID).enqueue(InstaApi.generateCallback(context, object : InstaApiCallback() {
                override fun success(jsonResponse: JSONObject) {
                    if (!jsonResponse.optBoolean("success")) {
                        toggleLike(button, item)
                    }
                }

                override fun failure(context: Context, jsonResponse: JSONObject?) = toggleLike(button, item)
            }))
        }
    }

    private fun toggleLike(button: Button, photo: Photo) {
        photo.isLiked = !photo.isLiked
        button.isSelected = photo.isLiked
    }

    init {

        if (recyclerView.layoutManager is LinearLayoutManager) {
            val llm = recyclerView.layoutManager as LinearLayoutManager
            recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)

                    if (!currentlyLoading && canLoadMore &&
                            llm.itemCount <= llm.findLastVisibleItemPosition() + visibleThreshold) {
                        currentlyLoading = true
                        onLoadMoreListener?.onLoadMore()
                    }
                }
            })
        }

    }

    override fun getItemViewType(position: Int): Int {
        if (position == 0) {
            return VIEW_TYPE_HEADER
        } else {
            if (dataset.get(position - 1) == null) {
                return VIEW_TYPE_LOADING
            } else {
                if(recyclerView.layoutManager!!::class == LinearLayoutManager::class) {
                    return VIEW_TYPE_PHOTO
                } else {
                    return VIEW_TYPE_PHOTO_GRID
                }
            }
        }
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val frameLayout = FrameLayout(parent.context)
                frameLayout.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                HeaderViewHolder(frameLayout)

            }
            VIEW_TYPE_PHOTO -> {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.adapter_feed_item, parent, false) as View
                PhotoViewHolder(v)
            }
            VIEW_TYPE_PHOTO_GRID -> {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.adapter_feed_item_grid, parent, false) as ImageView
                PhotoGridViewHolder(v)
            }
            else -> {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.adapter_feed_loading, parent, false) as LinearLayout
                ProgressViewHolder(v)
            }
        }
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {

        when(holder){
            is PhotoViewHolder->{
                val feedItem = dataset[position - 1]!!

                val image = holder.ivPhoto
                image.setAspectRatio(feedItem.regularHeight / feedItem.regularWidth.toFloat())

                holder.progressBar.visibility = View.VISIBLE

                val displayNameBuilder = SpannableStringBuilder()
                        .bold { append(feedItem.displayName) }
                        .setClickableSpan(feedItem.displayName, 0) {
                            clickListeners?.moveToProfileSubFragment(feedItem.displayName)
                        }

                holder.tvProfileName.text = displayNameBuilder

                holder.tvDistance.text = if (feedItem.distance > 0) Utils.formatDistance(feedItem.distance) else ""

                if (feedItem.locationName.isNotBlank()) {
                    holder.tvLocationName.visibility = View.VISIBLE
                    val locationNameBuilder = SpannableStringBuilder()
                            .append(feedItem.locationName)
                            .setClickableSpan(feedItem.locationName, 0) {
                                clickListeners?.moveToMapSubFragment()
                            }
                    holder.tvLocationName.text = locationNameBuilder
                } else {
                    holder.tvLocationName.visibility = View.GONE
                }

                holder.btLike.isSelected = feedItem.isLiked

                val likeBuilder = SpannableStringBuilder()

                if (feedItem.previewLikes.isNotEmpty()) {

                    val joinedLikes = feedItem.previewLikes.joinToString(", ")
                    likeBuilder.append("Liked by ")
                            .bold { append(joinedLikes) }
                    val remaining = feedItem.totalLikes - feedItem.previewLikes.size
                    if (remaining > 0) {
                        likeBuilder.append(" and ")
                                .bold { append("$remaining ${if (remaining > 1) "others" else "other"}") }
                    }
                    feedItem.previewLikes.forEach {
                        likeBuilder.setClickableSpan(it) {
                            clickListeners?.moveToProfileSubFragment(it)

                        }
                    }
                    likeBuilder.setClickableSpan(likeBuilder.toString().substringAfter(" and ")) {
                        clickListeners?.moveToUserListSubFragmentWithCall(UserListSubFragment.CallType.LIKES, feedItem.photoID)
                    }

                } else {
                    if (feedItem.totalLikes > 0) {
                        likeBuilder.bold { append("${feedItem.totalLikes} ${if (feedItem.totalLikes > 1) "likes" else "like"}") }
                    }

                    likeBuilder.setClickableSpan(likeBuilder.toString()) {
                        clickListeners?.moveToUserListSubFragmentWithCall(UserListSubFragment.CallType.LIKES, feedItem.photoID)
                    }
                }
                holder.tvLikeText.text = likeBuilder

                if(feedItem.caption.isNotBlank()){
                    holder.tvCaption.visibility = View.VISIBLE
                    val captionBuilder = SpannableStringBuilder()
                            .bold { append("${feedItem.displayName} ") }
                            .append(feedItem.caption)
                            .setClickableSpan(feedItem.displayName,0){
                                clickListeners?.moveToProfileSubFragment(feedItem.displayName)
                            }
                    holder.tvCaption.text = captionBuilder
                } else {
                    holder.tvCaption.visibility = View.GONE
                }

                if (feedItem.previewComments.isNotEmpty()){
                    holder.tvCommentPreviews.visibility = View.VISIBLE
                    val commentPreviewBuilder = SpannableStringBuilder()
                    feedItem.previewComments.forEach {
                        commentPreviewBuilder.bold { append("${it.first} ") }
                                .append(it.second)
                                .append("\n")
                                .setClickableSpan(it.first,0){
                                    clickListeners?.moveToProfileSubFragment(it.first)
                                }
                    }
                } else {
                    holder.tvCommentPreviews.visibility = View.GONE
                }

                if(feedItem.totalComments - feedItem.previewComments.size>0){
                    holder.tvCommentText.visibility = View.VISIBLE
                    val text = "View all ${feedItem.totalComments} comments"
                    val commentBuilder = SpannableStringBuilder()
                            .append(text)
                            .setClickableSpan(text, 0, ContextCompat.getColor(context, R.color.grey500)){
                                clickListeners?.moveToCommentsSubFragment(feedItem.photoID)
                            }
                    holder.tvCommentText.text = commentBuilder
                }else {
                    holder.tvCommentText.visibility = View.GONE
                }

                Glide.with(context)
                        .load(feedItem.profileImage)
//                .placeholder(R.drawable.icon_android)
                        .into(holder.ivProfileHead)


                if (feedItem.regularUrl.isNotBlank()) {
                    Glide.with(context)
                            .load(feedItem.regularUrl)
//                    .placeholder(R.drawable.icon_placeholder)
                            .listener(object : RequestListener<Drawable> {
                                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
                                    return false
                                }

                                override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                                    holder.progressBar.visibility = View.GONE
                                    return false
                                }
                            })
                            .into(holder.ivPhoto)
                } else {
                    Glide.with(context).clear(holder.ivPhoto)
                    holder.ivPhoto.setImageDrawable(null)
                }


                holder.btLike.setOnClickListener {
                    handleLike(holder.btLike, feedItem)
                }

                holder.btComment.setOnClickListener {
                    clickListeners?.moveToCommentsSubFragment(feedItem.displayName)
                }

                holder.tvDate.text = Utils.formatDate(feedItem.timestamp)
            }
            is HeaderViewHolder->{
                holder.container.removeAllViews()
                holder.container.addView(header)
            }
            is PhotoGridViewHolder->{
                val feedItem = dataset[position - 1]!!
                if (feedItem.smallUrl.isNotBlank()) {
                    Glide.with(context)
                            .load(feedItem.smallUrl)
                            .into(holder.ivPhoto)
                } else {
                    Glide.with(context).clear(holder.ivPhoto)
                    holder.ivPhoto.setImageDrawable(null)
                }

            }
        }


    }

    override fun getItemId(position: Int): Long {
        if (position == 0){
            return 0L
        }else{
            return dataset[position-1]?.photoID?.hashCode()?.toLong()?: Random().nextLong()
        }

    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount(): Int {
        return dataset.size + 1
    }

    interface OnLoadMoreListener {
        fun onLoadMore()
    }
}