package com.smart.adapter.layoutmanager

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.smart.adapter.SmartViewPager2Adapter

/**
 * @Author leo
 * @Date 2023/10/8
 * 改变切换速度
 */
class ScrollSpeedManger : LinearLayoutManager {
    private lateinit var viewPager2: ViewPager2
    private lateinit var smartViewPager2Adapter: SmartViewPager2Adapter
    constructor(viewPager2: ViewPager2, smartViewPager2Adapter: SmartViewPager2Adapter,linearLayoutManager:LinearLayoutManager ) : super(viewPager2.context, linearLayoutManager.orientation, false) {
        this.viewPager2 = viewPager2
        this.smartViewPager2Adapter = smartViewPager2Adapter
    }

    override fun smoothScrollToPosition(recyclerView: RecyclerView?, state: RecyclerView.State?, position: Int) {
        val linearSmoothScroller: LinearSmoothScroller = object : LinearSmoothScroller(recyclerView!!.context) {
            override fun calculateTimeForDeceleration(dx: Int): Int {
                return smartViewPager2Adapter.getScrollTime()
            }
        }
        linearSmoothScroller.targetPosition = position
        startSmoothScroll(linearSmoothScroller)
    }

    companion object{
        @JvmStatic
        fun reflectLayoutManager(viewPager2: ViewPager2, smartViewPager2Adapter: SmartViewPager2Adapter) {
            if (smartViewPager2Adapter.getScrollTime() < 100) return
            try {
                val recyclerView = viewPager2.getChildAt(0) as RecyclerView
                recyclerView.overScrollMode = RecyclerView.OVER_SCROLL_NEVER
                val speedManger = (recyclerView.layoutManager as LinearLayoutManager?)?.let { ScrollSpeedManger(viewPager2,smartViewPager2Adapter, it) }
                recyclerView.layoutManager = speedManger
                val LayoutMangerField = ViewPager2::class.java.getDeclaredField("mLayoutManager")
                LayoutMangerField.isAccessible = true
                LayoutMangerField[viewPager2] = speedManger
                val pageTransformerAdapterField = ViewPager2::class.java.getDeclaredField("mPageTransformerAdapter")
                pageTransformerAdapterField.isAccessible = true
                val mPageTransformerAdapter = pageTransformerAdapterField[viewPager2]
                if (mPageTransformerAdapter != null) {
                    val aClass: Class<*> = mPageTransformerAdapter.javaClass
                    val layoutManager = aClass.getDeclaredField("mLayoutManager")
                    layoutManager.isAccessible = true
                    layoutManager[mPageTransformerAdapter] = speedManger
                }
                val scrollEventAdapterField = ViewPager2::class.java.getDeclaredField("mScrollEventAdapter")
                scrollEventAdapterField.isAccessible = true
                val mScrollEventAdapter = scrollEventAdapterField[viewPager2]
                if (mScrollEventAdapter != null) {
                    val aClass: Class<*> = mScrollEventAdapter.javaClass
                    val layoutManager = aClass.getDeclaredField("mLayoutManager")
                    layoutManager.isAccessible = true
                    layoutManager[mScrollEventAdapter] = speedManger
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


}