/*
 * MIT License
 *
 * Copyright (c) 2018 Alibaba Group
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tmall.wireless.tangram3;

import android.content.Context;
import android.os.Build.VERSION;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import com.alibaba.android.vlayout.LayoutViewFactory;
import com.alibaba.android.vlayout.Range;
import com.alibaba.android.vlayout.VirtualLayoutManager;
import com.alibaba.android.vlayout.VirtualLayoutManager.LayoutParams;
import com.alibaba.android.vlayout.extend.InnerRecycledViewPool;
import com.alibaba.android.vlayout.extend.PerformanceMonitor;
import com.tmall.wireless.tangram3.core.adapter.GroupBasicAdapter;
import com.tmall.wireless.tangram3.core.service.ServiceManager;
import com.tmall.wireless.tangram3.dataparser.DataParser;
import com.tmall.wireless.tangram3.dataparser.IAdapterBuilder;
import com.tmall.wireless.tangram3.dataparser.concrete.BaseCellBinder;
import com.tmall.wireless.tangram3.dataparser.concrete.BaseCellBinderResolver;
import com.tmall.wireless.tangram3.dataparser.concrete.Card;
import com.tmall.wireless.tangram3.dataparser.concrete.ComponentInfo;
import com.tmall.wireless.tangram3.eventbus.BusSupport;
import com.tmall.wireless.tangram3.structure.BaseCell;
import com.tmall.wireless.tangram3.support.BannerSupport;
import com.tmall.wireless.tangram3.support.ExposureSupport;
import com.tmall.wireless.tangram3.support.InternalErrorSupport;
import com.tmall.wireless.tangram3.support.SimpleClickSupport;
import com.tmall.wireless.tangram3.support.TimerSupport;
import com.tmall.wireless.tangram3.util.ImageUtils;
import com.tmall.wireless.tangram3.util.Preconditions;
import com.tmall.wireless.tangram3.util.Predicate;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * {@link O} is the type of data, {@link T} is the array type of data
 *
 * @author kellen
 * @author villadora
 * @since 1.0.0
 */
public class BaseTangramEngine<O, T> implements ServiceManager {


    private Map<Class<?>, Object> mServices = new HashMap<>();

    @NonNull
    private final Context mContext;

    private RecyclerView mContentView;

    private final VirtualLayoutManager mLayoutManager;

    protected GroupBasicAdapter<Card, BaseCell> mGroupBasicAdapter;

    private final DataParser<O, T> mDataParser;

    private final IAdapterBuilder<Card, BaseCell> mAdapterBuilder;

    private PerformanceMonitor mPerformanceMonitor;

    private boolean isSupportRx;

    public BaseTangramEngine(@NonNull final Context context,
                             @NonNull final DataParser<O, T> dataParser,
                             @NonNull final IAdapterBuilder<Card, BaseCell> adapterBuilder) {
        //noinspection ConstantConditions
        Preconditions.checkArgument(context != null, "context is null");
        this.mContext = context;
        this.mLayoutManager = new VirtualLayoutManager(mContext);

        this.mLayoutManager.setLayoutViewFactory(new LayoutViewFactory() {
            @Override
            public View generateLayoutView(@NonNull Context context) {
                ImageView imageView = ImageUtils.createImageInstance(context);
                return imageView != null ? imageView : new View(context);
            }
        });

        this.mDataParser = Preconditions.checkNotNull(dataParser, "dataParser in constructor should not be null");
        this.mAdapterBuilder = Preconditions.checkNotNull(adapterBuilder, "adapterBuilder in constructor should not be null");
    }

    @NonNull
    public Context getContext() {
        return mContext;
    }

    /**
     * @return Return the recyclerView binded to Tangram. It is suggested to call {@link #bindView(RecyclerView)} first
     * before call this method.
     */
    public RecyclerView getContentView() {
        return mContentView;
    }

    /**
     * @return Adatepr binded to recyclerView.
     */
    public GroupBasicAdapter<Card, ?> getGroupBasicAdapter() {
        return mGroupBasicAdapter;
    }

    /**
     * Be careful about doing operations in LayoutManger, it may break existing contract
     *
     * @return internal LayoutManager
     */
    public VirtualLayoutManager getLayoutManager() {
        return mLayoutManager;
    }

    /**
     * Add a custom performance monitor to record performance
     *
     * @param performanceMonitor
     */
    public void setPerformanceMonitor(
            PerformanceMonitor performanceMonitor) {
        mPerformanceMonitor = performanceMonitor;
    }

    /**
     * Bind a recyclerView to Tangram. After calling this, {@link GroupBasicAdapter}, {@link VirtualLayoutManager} are auto binded.
     *
     * @param view A plain recyclerView with no adapter or layoutManager binded.
     */
    public void bindView(@NonNull final RecyclerView view) {
        //noinspection ConstantConditions
        Preconditions.checkArgument(view != null, "view must not be null");
        if (mContentView != null) {
            mContentView.setAdapter(null);
            mContentView.setLayoutManager(null);
        }

        this.mContentView = view;
        this.mContentView.setLayoutManager(mLayoutManager);
        mLayoutManager.setPerformanceMonitor(mPerformanceMonitor);
        if (mGroupBasicAdapter == null) {
            this.mGroupBasicAdapter = mAdapterBuilder.newAdapter(mContext, mLayoutManager, this);
            mGroupBasicAdapter.setPerformanceMonitor(mPerformanceMonitor);
            mGroupBasicAdapter.setErrorSupport(getService(InternalErrorSupport.class));
        }

        if (mContentView.getRecycledViewPool() != null) {
            mContentView.setRecycledViewPool(new InnerRecycledViewPool(mContentView.getRecycledViewPool()));
        }

        register(GroupBasicAdapter.class, mGroupBasicAdapter);
        register(RecyclerView.RecycledViewPool.class, mContentView.getRecycledViewPool());

        this.mContentView.setAdapter(mGroupBasicAdapter);
        if (VERSION.SDK_INT < 21) {
            this.mContentView.setChildDrawingOrderCallback(new DrawingOrderCallback());
        }
    }

    /**
     * Unbind the adapter and layoutManger to recyclerView. And also set null to them.
     */
    public void unbindView() {
        if (mContentView != null) {
            this.mContentView.setAdapter(null);
            this.mContentView.setLayoutManager(null);
            this.mContentView = null;
        }
    }

    /**
     * register cell after engine has been created
     *
     * @param type
     * @param viewClz
     * @param <V>
     */
    public <V extends View> void registerCell(String type, @NonNull Class<V> viewClz) {
        BaseCellBinderResolver baseCellBinderResolver = getService(BaseCellBinderResolver.class);
        MVHelper mMVHelper = getService(MVHelper.class);
        if (baseCellBinderResolver != null && mMVHelper != null && mMVHelper.resolver() != null) {
            baseCellBinderResolver.register(type, new BaseCellBinder<>(viewClz, mMVHelper));
            mMVHelper.resolver().register(type, viewClz);
        }

    }

    /**
     * Call this when your activity is ready to destory to clear inner resource.
     */
    public void destroy() {
        if (mContentView != null) {
            if (mGroupBasicAdapter != null) {
                mGroupBasicAdapter.destroy();
            }
            mContentView.setAdapter(null);
            mContentView = null;
        }
        TimerSupport timerSupport = getService(TimerSupport.class);
        if (timerSupport != null) {
            timerSupport.clear();
        }
        SimpleClickSupport simpleClickSupport = getService(SimpleClickSupport.class);
        if (simpleClickSupport != null) {
            simpleClickSupport.destroy();
        }
        ExposureSupport exposureSupport = getService(ExposureSupport.class);
        if (exposureSupport != null) {
            exposureSupport.destroy();
        }
        BusSupport busSupport = getService(BusSupport.class);
        if (busSupport != null) {
            busSupport.shutdown();
        }
        BannerSupport bannerSupport = getService(BannerSupport.class);
        if (bannerSupport != null) {
            bannerSupport.destroy();
        }
        MVHelper mvHelper = getService(MVHelper.class);
        if (mvHelper != null) {
            mvHelper.renderManager().destroyRenderService();
        }
    }

    /**
     * Append original data with type {@link T} to Tangram. It cause full screen item's rebinding, be careful.
     *
     * @param data Original data with type {@link T}.
     */
    @Deprecated
    public void appendData(@Nullable T data) {
        Preconditions.checkState(mGroupBasicAdapter != null, "Must call bindView() first");

        appendData(mDataParser.parseGroup(data, this));
    }

    /**
     * Insert original data to Tangram at target position. It cause full screen item's rebinding, be careful.
     *
     * @param position Target insert position.
     * @param data     Original data with type {@link T}.
     */
    @Deprecated
    public void insertData(int position, @Nullable T data) {
        Preconditions.checkState(mGroupBasicAdapter != null, "Must call bindView() first");

        insertData(position, mDataParser.parseGroup(data, this));
    }

    /**
     * Replace original data to Tangram at target position. It cause full screen item's rebinding, be careful.
     *
     * @param position Target insert position.
     * @param data     Original data with type {@link T}.
     */
    @Deprecated
    public void replaceData(int position, @Nullable T data) {
        Preconditions.checkState(mGroupBasicAdapter != null, "Must call bindView() first");

        replaceData(position, mDataParser.parseGroup(data, this));
    }

    /**
     * Set original data list with type {@link T} in Tangram.
     *
     * @param data Original data with type {@link T}.
     */
    public void setData(@Nullable T data) {
        Preconditions.checkState(mGroupBasicAdapter != null, "Must call bindView() first");

        List<Card> cards = mDataParser.parseGroup(data, this);
        this.setData(cards);
    }

    /**
     * Set parsed data list with type {@link Card} in Tangram
     *
     * @param data Parsed data list.
     */
    public void setData(@Nullable List<Card> data) {
        Preconditions.checkState(mGroupBasicAdapter != null, "Must call bindView() first");
        MVHelper mvHelper = (MVHelper) mServices.get(MVHelper.class);
        if (mvHelper != null) {
            mvHelper.reset();
            mvHelper.renderManager().onDownloadTemplate();
        }
        this.mGroupBasicAdapter.setData(data);
    }

    /**
     * Append parsed data list with type {@link Card} in Tangram. It cause full screen item's rebinding, be careful.
     *
     * @param data Parsed data list.
     */
    @Deprecated
    public void appendData(@Nullable List<Card> data) {
        Preconditions.checkState(mGroupBasicAdapter != null, "Must call bindView() first");
        this.mGroupBasicAdapter.appendGroup(data);
    }

    /**
     * Insert parsed data to Tangram at target position. It cause full screen item's rebinding, be careful.
     *
     * @param position Target insert position.
     * @param data     Parsed data list.
     */
    @Deprecated
    public void insertData(int position, @Nullable List<Card> data) {
        Preconditions.checkState(mGroupBasicAdapter != null, "Must call bindView() first");
        this.mGroupBasicAdapter.insertGroup(position, data);
    }

    /**
     * Replace original data to Tangram at target position. It cause full screen item's rebinding, be careful.
     *
     * @param position Target replace position.
     * @param data     Parsed data list.
     */
    @Deprecated
    public void replaceData(int position, @Nullable List<Card> data) {
        Preconditions.checkState(mGroupBasicAdapter != null, "Must call bindView() first");
        this.mGroupBasicAdapter.replaceGroup(position, data);
    }

    /**
     * Remove a card at target card position. It cause full screen item's rebinding, be careful.
     *
     * @param position the position of card in group
     */
    @Deprecated
    public void removeData(int position) {
        Preconditions.checkState(mGroupBasicAdapter != null, "Must call bindView() first");
        this.mGroupBasicAdapter.removeGroup(position);
    }

    /**
     * Remove the target card from list. It cause full screen item's rebinding, be careful.
     *
     * @param data Target card
     */
    @Deprecated
    public void removeData(Card data) {
        Preconditions.checkState(mGroupBasicAdapter != null, "Must call bindView() first");
        this.mGroupBasicAdapter.removeGroup(data);
    }

    /**
     * @param card
     * @return card ragne of given instance
     */
    public Range<Integer> getCardRange(Card card) {
        Preconditions.checkState(mGroupBasicAdapter != null, "Must call bindView() first");
        return this.mGroupBasicAdapter.getCardRange(card);
    }

    /**
     * Get card range by id
     *
     * @param id card id
     * @return range instance
     */
    public Range<Integer> getCardRange(String id) {
        Preconditions.checkState(mGroupBasicAdapter != null, "Must call bindView() first");
        return this.mGroupBasicAdapter.getCardRange(id);
    }

    /**
     * @param position cell's adapter position
     * @return the card index of given cell's position
     */
    public int findCardIdxFor(int position) {
        Preconditions.checkState(mGroupBasicAdapter != null, "Must call bindView() first");
        return this.mGroupBasicAdapter.findCardIdxFor(position);
    }

    /**
     * @param cell cell object
     * @return the card index of given cell object
     */
    public int findCardIdxFor(BaseCell cell) {
        Preconditions.checkState(mGroupBasicAdapter != null, "Must call bindView() first");
        return this.mGroupBasicAdapter.findCardIdxFor(cell);
    }

    /**
     * @param id card id
     * @return card instance
     */
    public Card getCardById(String id) {
        Preconditions.checkState(mGroupBasicAdapter != null, "Must call bindView() first");
        return this.mGroupBasicAdapter.getCardById(id);
    }

    /**
     * @param type cell's type
     * @return last appearance position
     */
    public int findLastPositionOfCell(String type) {
        Preconditions.checkState(mGroupBasicAdapter != null, "Must call bindView() first");
        return this.mGroupBasicAdapter.findLastPositionOfCell(type);
    }

    /**
     * @param type cell's type
     * @return first appearance position
     */
    public int findFirstPositionOfCell(String type) {
        Preconditions.checkState(mGroupBasicAdapter != null, "Must call bindView() first");
        return this.mGroupBasicAdapter.findFirstPositionOfCell(type);
    }

    /**
     * Parse original data with type {@link T} into model data list with type {@link Card}
     *
     * @param data Original data.
     * @return Parsed data list.
     */
    public List<Card> parseData(@Nullable T data) {
        List<Card> cardList = mDataParser.parseGroup(data, this);
        MVHelper mvHelper = (MVHelper) mServices.get(MVHelper.class);
        if (mvHelper != null) {
            mvHelper.renderManager().onDownloadTemplate();
        }
        return cardList;
    }

    /**
     * Parse original data with type {@link T} into model data list with type {@link BaseCell}
     *
     * @param data   Original data.
     * @param parent the parent group to hold the parsed list.
     * @return Parsed data list.
     * @since 3.0.0
     */
    public List<BaseCell> parseComponent(@Nullable Card parent, @Nullable T data, @Nullable Map<String, ComponentInfo> componentInfoMap) {
        return mDataParser.parseComponent(data, parent, this, componentInfoMap);
    }

    /**
     * Parse original data with type {@link O} into model data with type {@link Card}
     *
     * @param data Original data.
     * @return Parsed data.
     * @since 3.0.0
     */
    public Card parseSingleData(@Nullable O data) {
        return mDataParser.parseSingleGroup(data, this);
    }

    /**
     * Parse original data with type {@link O} into model data with type {@link BaseCell}
     *
     * @param parent the parent group to hold parsed object.
     * @param data   Original data.
     * @return Parsed data.
     * @since 3.0.0
     */
    public BaseCell parseSingleComponent(@Nullable Card parent, @Nullable O data, @Nullable Map<String, ComponentInfo> componentInfoMap) {
        return mDataParser.parseSingleComponent(data, parent, this, componentInfoMap);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <S> void register(@NonNull Class<S> type, @NonNull S service) {
        Preconditions.checkArgument(type != null, "type is null");
        mServices.put(type, type.cast(service));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <S> S getService(@NonNull Class<S> type) {
        Object service = mServices.get(type);
        if (service == null) {
            return null;
        }
        return type.cast(service);
    }

    @Override
    public boolean supportRx() {
        return isSupportRx;
    }

    @SuppressWarnings("unchecked")
    @NonNull
    public List<Card> findGroups(final Predicate<Card> predicate) {
        Preconditions.checkState(mGroupBasicAdapter != null, "Must call bindView() first");

        List<Card> groups = (List<Card>) mGroupBasicAdapter.getGroups();
        if (predicate == null) {
            return groups;
        }

        List<Card> rs = new LinkedList<Card>();

        for (Card g : groups) {
            if (predicate.isMatch(g)) {
                rs.add(g);
            }
        }

        return rs;
    }

    private class DrawingOrderCallback implements RecyclerView.ChildDrawingOrderCallback {

        int[] viewIndex = new int[32];

        int[] zIndex = new int[32];

        private int[] doubleIndex(int[] index) {
            if (index == null) {
                return null;
            }
            int[] newIndex = new int[index.length * 2];
            System.arraycopy(index, 0, newIndex, 0, index.length);
            return newIndex;
        }

        private void clearIndex(int[] index) {
            if (index == null) {
                return;
            }
            Arrays.fill(index, 0);
        }

        int partition(int[] unsortedZIndex, int[] unsortedViewIndex, int low, int high) {
            int pivot = unsortedZIndex[low];
            int viewPivot = unsortedViewIndex[low];
            while (low < high) {
                while (low < high && unsortedZIndex[high] > pivot) high--;
                unsortedZIndex[low] = unsortedZIndex[high];
                unsortedViewIndex[low] = unsortedViewIndex[high];
                while (low < high && unsortedZIndex[low] <= pivot) low++;
                unsortedZIndex[high] = unsortedZIndex[low];
                unsortedViewIndex[high] = unsortedViewIndex[high];
            }
            unsortedZIndex[low] = pivot;
            unsortedViewIndex[low] = viewPivot;
            return low;
        }

        void quickSort(int[] unsortedZIndex, int[] unsortedViewIndex, int low, int high) {
            int loc = 0;
            if (low < high) {
                loc = partition(unsortedZIndex, unsortedViewIndex, low, high);
                quickSort(unsortedZIndex, unsortedViewIndex, low, loc - 1);
                quickSort(unsortedZIndex, unsortedViewIndex, loc + 1, high);
            }
        }

        void bubbleSort(int[] unsortedZIndex, int[] unsortedViewIndex, int length) {
            for (int i = 0; i < length - 1; i++) {
                for (int j = length - 1; j > i; j--) {
                    if (unsortedZIndex[j] < unsortedZIndex[j - 1]) {
                        int temp = unsortedZIndex[j];
                        unsortedZIndex[j] = unsortedZIndex[j - 1];
                        unsortedZIndex[j - 1] = temp;

                        temp = unsortedViewIndex[j];
                        unsortedViewIndex[j] = unsortedViewIndex[j - 1];
                        unsortedViewIndex[j - 1] = temp;
                    }
                }
            }
        }

        @Override
        public int onGetChildDrawingOrder(int childCount, int i) {
            if (zIndex.length < childCount) {
                zIndex = doubleIndex(zIndex);
                viewIndex = doubleIndex(viewIndex);
            }
            // check all current zIndex
            for (int j = 0; j < childCount; j++) {
                View child = mContentView.getChildAt(j);
                if (child != null) {
                    LayoutParams layoutParams = (LayoutParams) child.getLayoutParams();
                    zIndex[j] = layoutParams.zIndex;
                } else {
                    zIndex[j] = 0;
                }
                viewIndex[j] = j;
            }
            // reorder drawing by zIndex
            bubbleSort(zIndex, viewIndex, childCount);

            int result = viewIndex[i];
            clearIndex(zIndex);
            clearIndex(viewIndex);
            return result;
        }
    }

    public <S> Map<Class<S>, S> getServices() {
        Map<Class<S>, S> map = new HashMap<>();
        for (Class<?> aClass : mServices.keySet()) {

            S s = (S) getService(aClass);
            map.put((Class<S>) aClass, s);
        }
        return map;
    }

    public <S> void copyServices(Map<Class<S>, S> services) {
        for (Map.Entry<Class<S>, S> entry : services.entrySet()) {
            if (!mServices.containsKey(entry.getKey())) {
                register(entry.getKey(), entry.getValue());
            }
        }
    }
}
