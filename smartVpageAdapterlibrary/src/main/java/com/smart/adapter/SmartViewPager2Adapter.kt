package com.smart.adapter

import android.view.MotionEvent
import androidx.annotation.IntRange
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.smart.adapter.interf.OnLoadMoreListener
import com.smart.adapter.interf.OnRefreshListener
import com.smart.adapter.interf.OnRefreshLoadMoreListener
import com.smart.adapter.interf.SmartFragmentImpl
import com.smart.adapter.interf.SmartFragmentTypeExEntity
import com.smart.adapter.layoutmanager.ScrollSpeedManger
import com.smart.adapter.transformer.SmartTransformer
import com.smart.adapter.transformer.StereoPagerTransformer
import com.smart.adapter.transformer.StereoPagerVerticalTransformer
import com.smart.adapter.transformer.TransAlphScaleFormer
import com.smart.adapter.util.ScreenUtils
import com.smart.adapter.util.ViewPager2Util
import java.lang.ref.WeakReference


/**
 * @Author leo2
 * @Date 2023/9/4
 */
class SmartViewPager2Adapter : FragmentStateAdapter {
    private val mDataList = mutableListOf<SmartFragmentTypeExEntity>()

    private val mPreloadDataList by lazy {
        mutableListOf<SmartFragmentTypeExEntity>()
    }

    private lateinit var mViewPager2: ViewPager2
    private var mRefreshListener: OnRefreshListener? = null
    private var mLoadMoreListener: OnLoadMoreListener? = null
    private val fragments = mutableMapOf<Int, Class<*>>()
    private var defaultFragment: Class<*>? = null

    //预加载litmit,当滑动到只剩余limit个数后，触发加载刷新监听
    //如果，当前个数小于mPreLoadLimit*2+1时，优先触发loadMore监听
    private var mPreLoadLimit: Int = 3
    private var mLeftMargin: Int = 0
    private var mRightMargin: Int = 0
    private var mScreenMinNum = 1
    private var mAutoLoop = false
    private val mLoopTask by lazy {
        AutoLoopTask(mViewPager2, this)
    }
    private var mLoopTime = 3000L
    private var mLifecycle: Lifecycle? = null
    private val mLifecycleEventObserver by lazy {
        LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    start()
                }

                Lifecycle.Event.ON_PAUSE -> {
                    stop()
                }

                Lifecycle.Event.ON_DESTROY -> {

                }

                else -> {}
            }
        }
    }

    // 轮播切换时间
    private var mScrollTime: Int = 600


    constructor(fragmentActivity: FragmentActivity, bindViewPager2: ViewPager2) : super(
        fragmentActivity
    ) {
        this.mViewPager2 = bindViewPager2
        this.mLifecycle = fragmentActivity.lifecycle
        initSmartViewPager()
    }

    constructor(fragment: Fragment, bindViewPager2: ViewPager2) : super(fragment) {
        this.mViewPager2 = bindViewPager2
        this.mLifecycle = fragment.lifecycle
        initSmartViewPager()
    }

    constructor(
        fragmentManager: FragmentManager,
        lifecycle: Lifecycle,
        bindViewPager2: ViewPager2,
    ) : super(fragmentManager, lifecycle) {
        //源码都会走这里
        this.mViewPager2 = bindViewPager2
        this.mLifecycle = lifecycle
        initSmartViewPager()
    }


    fun setOnRefreshListener(listener: OnRefreshListener): SmartViewPager2Adapter {
        this.mRefreshListener = listener
        return this
    }

    fun setLoadMoreListener(listener: OnLoadMoreListener): SmartViewPager2Adapter {
        this.mLoadMoreListener = listener
        return this
    }

    fun setOnRefreshLoadMoreListener(listener: OnRefreshLoadMoreListener): SmartViewPager2Adapter {
        this.mRefreshListener = listener
        this.mLoadMoreListener = listener
        checkIndexAndCallBack(mViewPager2.currentItem)
        return this
    }

    private fun checkIndexAndCallBack(position: Int) {
        //如果是无线循环模式，则不支持监听
        if (mInfinite) {
            selectPosWithInfinite(position)
            return
        }

        if (mLoadMoreListener != null) {
            registLoadMoreOrNot(position)
        }
        if (mRefreshListener != null) {
            registRefreshOrNot(position)
        }
    }


    override fun createFragment(position: Int): Fragment {

        var bean = if (mInfinite) {
            mDataList[position % mDataList.size]
        } else {
            mDataList[position]
        }
        if (fragments.isEmpty()) {
            throw IllegalArgumentException("the fragments can not be empty")
        }
        var targetFragment = fragments[bean.getFragmentType()]
        var realFragment = targetFragment?.newInstance() as Fragment
        var smartFrgamentImpl = realFragment as SmartFragmentImpl
        smartFrgamentImpl.initSmartFragmentData(bean)
        return realFragment
    }

    override fun getItemCount() = if (mInfinite) {
        Int.MAX_VALUE
    } else {
        mDataList.size
    }

    override fun getItemId(position: Int): Long {
        return if (mInfinite) {
            if (mDataList.size < mScreenMinNum) {
                /*
                * 注意无线循环时，数据不足一屏时，重写getItemId hashCode值
                * */
                (mDataList[position % mDataList.size].hashCode() + position).toLong()
            } else {
                mDataList[position % mDataList.size].hashCode().toLong()
            }
        } else {
            mDataList[position % mDataList.size].hashCode().toLong()
        }
    }

    fun addData(list: MutableList<SmartFragmentTypeExEntity>): SmartViewPager2Adapter {
        if (list.isNullOrEmpty()) {
            return this
        }
        var lastIndex = mDataList.size
        mDataList.addAll(list)
        if (mInfinite) {
            updateDataWithInfinite()
        } else {
            updateLoadmore(lastIndex, list.size)
        }
        updateRefreshLoadMoreState()
        return this
    }

    fun addData(bean: SmartFragmentTypeExEntity): SmartViewPager2Adapter {
        if (bean == null) {
            return this
        }
        addData(mutableListOf(bean))
        return this
    }

    fun addFrontData(list: MutableList<SmartFragmentTypeExEntity>): SmartViewPager2Adapter {
        if (list.isNullOrEmpty()) {
            return this
        }
        mPreloadDataList.addAll(0, list)
        updateWithIdel(mViewPager2.scrollState)
        return this
    }

    fun addFrontData(bean: SmartFragmentTypeExEntity): SmartViewPager2Adapter {
        if (bean == null) {
            return this
        }
        addFrontData(mutableListOf(bean))
        return this
    }


    fun getItem(@IntRange(from = 0) position: Int): SmartFragmentTypeExEntity {
        return mDataList[position]
    }

    fun getItemOrNull(@IntRange(from = 0) position: Int): SmartFragmentTypeExEntity? {
        return mDataList.getOrNull(position)
    }

    /**
     * 如果返回 -1，表示不存在
     */
    fun getItemPosition(item: SmartFragmentTypeExEntity?): Int {
        return if (item != null && mDataList.isNotEmpty()) mDataList.indexOf(item) else -1
    }


    private fun initSmartViewPager() {

        if (mViewPager2 == null) {
            throw IllegalArgumentException("the bindView viewPager2 can not be null")
        }

        mViewPager2.registerOnPageChangeCallback(object : OnPageChangeCallback() {
            override fun onPageScrolled(
                position: Int,
                positionOffset: Float,
                positionOffsetPixels: Int,
            ) {
                super.onPageScrolled(position, positionOffset, positionOffsetPixels)
            }

            override fun onPageScrollStateChanged(state: Int) {
                super.onPageScrollStateChanged(state)
                updateWithIdel(state)
            }

            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                checkIndexAndCallBack(position)
            }
        })


        registerAdapterDataObserver(object : AdapterDataObserver() {
            override fun onChanged() {
                super.onChanged()
                if (mInfinite) {
                    var wholeNum = Int.MAX_VALUE / 2 / mDataList.size
                    mViewPager2.post {
                        mViewPager2.setCurrentItem(wholeNum * mDataList.size, false)
                    }
                }
            }
        })

        mViewPager2.getChildAt(0).setOnTouchListener { _, p1 ->
            when (p1?.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (mLoopTask != null) {
                        stop()
                    }
                }

                MotionEvent.ACTION_CANCEL,
                MotionEvent.ACTION_UP -> {
                    if (mLoopTask != null) {
                        start()
                    }
                }
            }
            false
        }
        ScrollSpeedManger.reflectLayoutManager(mViewPager2,this)
    }


    private fun updateLoadmore(tempSize: Int, netDataSize: Int) {
        notifyItemRangeChanged(tempSize, netDataSize)
    }


    private fun updateRefresh(tempSize: Int) {
        if (tempSize == 0) {
            //初始化数据为空时，front只能走notifyDataSetChanged
            notifyDataSetChanged()
        } else {
            mViewPager2.setCurrentItem(mPreloadDataList.size + mViewPager2.currentItem, false)
        }
    }

    //无线循环下，更新
    private fun updateDataWithInfinite() {
        notifyDataSetChanged()
    }

    //无线循环下，选择正确pos
    private fun selectPosWithInfinite(position: Int) {
        if (position <= Int.MAX_VALUE / 4 && mViewPager2.scrollState == ViewPager2.SCROLL_STATE_IDLE) {
            var wholeNum = Int.MAX_VALUE / 2 / mDataList.size
            mViewPager2.post {
                mViewPager2.setCurrentItem(wholeNum * mDataList.size + position, false)
            }
        }
    }


    /**
     * 空闲时才去更新数据
     * */
    private fun updateWithIdel(state: Int) {
        if (state == ViewPager2.SCROLL_STATE_IDLE && mPreloadDataList.isNotEmpty()) {
            var tempSize = mDataList.size
            mDataList.addAll(0, mPreloadDataList)
            if (mInfinite) {
                updateDataWithInfinite()
            } else {
                updateRefresh(tempSize)
            }
            mPreloadDataList.clear()
            updateRefreshLoadMoreState()
        }

        if (mInfinite) {
            selectPosWithInfinite(mViewPager2.currentItem)
        }
    }

    /**
     * 兜底策略，如果没有对应type的fragment则会用defaultFragment
     * 如果没有设置defaultFragment的话，则会取fragments里第一个添加元素，如果fragments为空则会报错
     * */
    fun addDefaultFragment(fragment: Class<*>): SmartViewPager2Adapter {
        defaultFragment = fragment
        return this
    }

    fun addFragment(type: Int, fragment: Class<*>): SmartViewPager2Adapter {
        fragments[type] = fragment
        if (defaultFragment == null) {
            defaultFragment = fragments[type]
        }
        return this
    }

    fun cancleOverScrollMode(): SmartViewPager2Adapter {
        ViewPager2Util.cancleViewPagerShadow(mViewPager2)
        return this
    }


    /**
     * 是否实现画廊
     */
    fun asGallery(leftMargin: Int, rightMargin: Int): SmartViewPager2Adapter {
        var recycleView = ViewPager2Util.getRecycleFromViewPager2(mViewPager2)
        mLeftMargin = leftMargin
        mRightMargin = rightMargin
        if (mViewPager2.orientation == ViewPager2.ORIENTATION_HORIZONTAL) {
            recycleView?.setPadding(leftMargin, 0, rightMargin, 0)
        } else {
            recycleView?.setPadding(0, leftMargin, 0, leftMargin)
        }
        recycleView?.clipToPadding = false
        calculateScreenMinNum()
        return this
    }

    private var mInfinite = false
    fun setInfinite(isInfinite: Boolean = true): SmartViewPager2Adapter {
        this.mInfinite = isInfinite
        mViewPager2.offscreenPageLimit = ViewPager2.OFFSCREEN_PAGE_LIMIT_DEFAULT
        calculateScreenMinNum()
        return this
    }


    private fun calculateScreenMinNum() {
        var screenWidth = ScreenUtils.getScreenWidth(mViewPager2.context)
        mScreenMinNum = screenWidth / (screenWidth - mLeftMargin - mRightMargin) + 2
    }


    fun setVertical(isVertical: Boolean): SmartViewPager2Adapter {
        mViewPager2.orientation = if (isVertical) {
            ViewPager2.ORIENTATION_VERTICAL
        } else {
            ViewPager2.ORIENTATION_HORIZONTAL
        }
        return this
    }


    fun setOffscreenPageLimit(limit: Int): SmartViewPager2Adapter {
        if (mInfinite) {
            mViewPager2.offscreenPageLimit = ViewPager2.OFFSCREEN_PAGE_LIMIT_DEFAULT
            return this
        }
        mViewPager2.offscreenPageLimit = limit
        return this
    }

    fun setPreLoadLimit(preLoadLimit: Int): SmartViewPager2Adapter {
        this.mPreLoadLimit = preLoadLimit
        return this
    }

    fun setPagerTransformer(smartTransformer: SmartTransformer): SmartViewPager2Adapter {
        when (smartTransformer) {
            SmartTransformer.TRANSFORMER_3D -> {
                mViewPager2.setPageTransformer(
                    if (mViewPager2.orientation == ViewPager2.ORIENTATION_HORIZONTAL) {
                        StereoPagerTransformer(mViewPager2.resources.displayMetrics.widthPixels.toFloat())
                    } else {
                        StereoPagerVerticalTransformer(mViewPager2.resources.displayMetrics.heightPixels.toFloat())
                    }
                )
            }

            SmartTransformer.TRANSFORMER_ALPHA_SCALE -> mViewPager2.setPageTransformer(
                TransAlphScaleFormer()
            )
        }
        return this
    }


    private var hasRefresh = true
    private var isRefreshing = false
    private fun registRefreshOrNot(currentPosition: Int) {
        //刷新和加载，同一时间段只允许一个进行(直至数据返回，或主动调用finishWithNoData)
        if (!hasRefresh || isRefreshing || isLoadMoring) {
            return
        }

        if (currentPosition <= mPreLoadLimit) {
            isRefreshing = true
            mRefreshListener?.onRefresh(this)
        }
    }

    private fun updateRefreshLoadMoreState() {
        isRefreshing = false
        isLoadMoring = false
    }


    private var hasLoadMore = true
    private var isLoadMoring = false
    private fun registLoadMoreOrNot(currentPosition: Int) {
        if (!hasLoadMore || isLoadMoring || isRefreshing) {
            return
        }

        val realPosition: Int = mDataList.size - 1 - currentPosition
        if (realPosition <= mPreLoadLimit) {
            isLoadMoring = true
            mLoadMoreListener?.onLoadMore(this)
        }
    }


    /**
     * 调用此方法将不再触发refresh监听
     * */
    fun finishRefreshWithNoMoreData() {
        hasRefresh = false
        updateRefreshLoadMoreState()
    }


    /**
     * 调用此方法将不再触发LoadMore监听
     * */
    fun finishLoadMoreWithNoMoreData() {
        hasLoadMore = false
        updateRefreshLoadMoreState()
    }


    internal class AutoLoopTask(viewPager2: ViewPager2, smartViewPager2Adapter: SmartViewPager2Adapter) : Runnable {
        private val referencePager: WeakReference<ViewPager2>
        private val referenceAdapter: WeakReference<SmartViewPager2Adapter>

        init {
            referencePager = WeakReference<ViewPager2>(viewPager2)
            referenceAdapter = WeakReference<SmartViewPager2Adapter>(smartViewPager2Adapter)
        }

        override fun run() {
            val viewPager2: ViewPager2? = referencePager.get()
            val smartViewPager2Adapter: SmartViewPager2Adapter? = referenceAdapter.get()
            if (viewPager2 != null && smartViewPager2Adapter?.mAutoLoop == true) {
                val count: Int = smartViewPager2Adapter.itemCount
                if (count == 0) {
                    return
                }
                //没有设置无线循，且size=1时，不进行滚动
                if (!smartViewPager2Adapter.mInfinite && count==1){
                    return
                }

                //没有设置无线循环时，达到最大size之后重置
                if (!smartViewPager2Adapter.mInfinite) {
                    if (viewPager2.currentItem == smartViewPager2Adapter.mDataList.size - 1) {
                        viewPager2.currentItem = 0
                    } else {
                        viewPager2.currentItem = viewPager2.currentItem + 1
                    }
                } else {
                    viewPager2.currentItem = viewPager2.currentItem + 1
                }
                viewPager2.postDelayed(smartViewPager2Adapter.mLoopTask, smartViewPager2Adapter.mLoopTime)
            }
        }
    }


    fun isAutoLoop(autoLoop: Boolean = true): SmartViewPager2Adapter {
        this.mAutoLoop = autoLoop
        return this
    }


    fun addLifecycleObserver(): SmartViewPager2Adapter {
        isAutoLoop()
        removeLifecycleObserver()
        mLifecycle?.addObserver(mLifecycleEventObserver)
        return this
    }

    private fun removeLifecycleObserver(){
        mLifecycle?.removeObserver(mLifecycleEventObserver)
    }


    fun setLoopTime(loopTime: Long): SmartViewPager2Adapter {
        this.mLoopTime = loopTime
        //限定最小滚动时间
        if (mLoopTime < 500L) {
            mLoopTime = 500L
        }
        return this
    }


    fun setScrollTime(scrollTime:Int):SmartViewPager2Adapter{
        this.mScrollTime = scrollTime
        return this
    }

    fun getScrollTime():Int{
        return mScrollTime
    }


    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        if (mAutoLoop) {
            start()
        }
    }

    fun start(): SmartViewPager2Adapter {
        stop()
        mViewPager2.postDelayed(mLoopTask, mLoopTime)
        return this;
    }

    fun stop(): SmartViewPager2Adapter {
        mViewPager2.removeCallbacks(mLoopTask)
        return this;
    }
}