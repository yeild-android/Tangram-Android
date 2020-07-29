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

package com.tmall.wireless.tangram3.structure;

import android.util.SparseArray;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.alibaba.fastjson.JSONObject;
import com.tmall.wireless.tangram3.Engine;
import com.tmall.wireless.tangram3.core.service.ServiceManager;
import com.tmall.wireless.tangram3.dataparser.concrete.Card;
import com.tmall.wireless.tangram3.dataparser.concrete.ComponentInfo;
import com.tmall.wireless.tangram3.dataparser.concrete.ComponentLifecycle;
import com.tmall.wireless.tangram3.dataparser.concrete.Style;
import com.tmall.wireless.tangram3.support.SimpleClickSupport;
import com.tmall.wireless.tangram3.util.BDE;
import com.tmall.wireless.tangram3.util.IInnerImageSetter;
import com.tmall.wireless.tangram3.util.ImageUtils;
import com.tmall.wireless.tangram3.util.LifeCycleProviderImpl;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by mikeafc on 16/4/25.
 */
public class BaseCell extends ComponentLifecycle implements View.OnClickListener {

    public enum GridDisplayType {
        inline, block
    }

    public static final BaseCell NaN = new NanBaseCell();

    private static AtomicLong sIdGen = new AtomicLong();

    public static boolean sIsGenIds = false;

    /**
     * cell's type
     */
    public String stringType;

    /**
     * parent's id
     */
    @Nullable
    public String parentId;

    /**
     * parent
     */
    public Card parent;

    /**
     * id of a cell
     */
    @Nullable
    public String id;

    /**
     * the natural position this cell in its parent
     */
    public int pos;

    /**
     * position that assigned from server side
     */
    public int position = -1;

    /**
     * cell's style
     */
    @NonNull
    public Style style;

    /**
     * item type for adapter.<br />
     * by default, the item type is calculated by {@link #stringType}, which means cell with same type share a recycler pool.
     * if you set a unique typeKey to cell, the item type is calculated by {@link #typeKey}, which measn cells with same typeKey share a recycler pool. This may be dangerous you must ensure the same typeKey must be assigned to the same type of cell.<br />
     * best practice is that if you have 10 cells with same type and need a certain one to a independent recycler pool.
     */
    public String typeKey;

    /**
     * inner use, item id for adapter.
     */
    public final long objectId;

    public ComponentInfo componentInfo;

    /**
     * the original json data
     */
    public JSONObject extras = new JSONObject();

    public GridDisplayType gridDisplayType = GridDisplayType.inline;

    public int colSpan = 1;

    private Map<String, Object> userParams;

    private Map<Integer, Integer> innerClickMap = new HashMap<>();

    @Nullable
    public ServiceManager serviceManager;

    public boolean mIsExposed = false;

    private SparseArray<Object> mTag;

    public BaseCell() {
        objectId = sIsGenIds ? sIdGen.getAndIncrement() : 0;
    }

    public BaseCell(String stringType) {
        setStringType(stringType);
        objectId = sIsGenIds ? sIdGen.getAndIncrement() : 0;
    }

    public void setStringType(String type) {
        stringType = type;
    }

    public void addUserParam(String key, Object value) {
        if (userParams == null) {
            userParams = new HashMap<>(32);
        }
        userParams.put(key, value);
    }

    @Nullable
    public Object getUserParam(String key) {
        if (userParams != null && userParams.containsKey(key)) {
            return userParams.get(key);
        }
        return null;
    }

    @Nullable
    public Map<String, Object> getAllUserParams() {
        return userParams;
    }

    @Override
    public void onClick(View v) {
        if (serviceManager != null) {
            SimpleClickSupport service = serviceManager.getService(SimpleClickSupport.class);
            if (service != null) {
                int eventType = this.pos;
                if (innerClickMap.containsKey(v.hashCode())) {
                    eventType = innerClickMap.get(v.hashCode()).intValue();
                }
                service.onClick(v, this, eventType);
            }
        }
    }

    public void setOnClickListener(View view, int eventType) {
        view.setOnClickListener(this);
        innerClickMap.put(view.hashCode(), Integer.valueOf(eventType));
    }

    public void clearClickListener(View view, int eventType) {
        view.setOnClickListener(null);
        innerClickMap.remove(view.hashCode());
    }

    /**
     * Do not call this method as its poor performance.
     */
    @Deprecated
    public final void notifyDataChange() {
        if (serviceManager instanceof Engine) {
            ((Engine) serviceManager).refresh(false);
        }
    }


    public final void doLoadImageUrl(ImageView view, String imgUrl) {
        if (serviceManager != null && serviceManager.getService(IInnerImageSetter.class) != null) {
            serviceManager.getService(IInnerImageSetter.class).doLoadImageUrl(view, imgUrl);
        } else {
            ImageUtils.doLoadImageUrl(view, imgUrl);
        }
    }

    /**
     * bind a tag to baseCell
     *
     * @param key
     * @param value
     */
    public void setTag(int key, Object value) {
        if (mTag == null) {
            mTag = new SparseArray<>();
        }
        mTag.put(key, value);
    }

    /**
     * get a tag from baseCell
     *
     * @param key
     * @return
     */
    public Object getTag(int key) {
        if (mTag != null) {
            return mTag.get(key);
        }
        return null;
    }

    public boolean isValid() {
        return true;
    }

    public static final class NanBaseCell extends BaseCell {
        @Override
        public boolean isValid() {
            return false;
        }
    }

    private LifeCycleProviderImpl<BDE> mLifeCycleProvider;


    public void emitNext(BDE event) {
        if (mLifeCycleProvider == null) {
            mLifeCycleProvider = new LifeCycleProviderImpl<>();
        }
        mLifeCycleProvider.emitNext(event);
    }

    public LifeCycleProviderImpl<BDE> getLifeCycleProvider() {
        return mLifeCycleProvider;
    }


}
