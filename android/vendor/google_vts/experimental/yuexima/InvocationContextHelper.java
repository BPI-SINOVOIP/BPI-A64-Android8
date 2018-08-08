/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tradefed.util;

import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.util.MultiMap;

import java.util.List;

/**
 * A helper class to handle attributes from IInvocationContext
 */
public class InvocationContextHelper {
    private IInvocationContext mContext;

    public InvocationContextHelper(IInvocationContext context) {
        mContext = context;
    }

    /**
     */
    public void addInvocationAttribute(String attributeName, String attributeValue) {
        mContext.addInvocationAttribute(attributeName, attributeValue);
    }

    /**
     * Set a single value to the invocation attribute with the given name.
     *
     * This will clear the attribute with the given name, leaving only one item
     * in the attribute multi map.
     *
     * @param attributeName key
     * @param attributeValue value
     */
    public void setInvocationAttribute(String attributeName, String attributeValue) {
        getAttributes().remove(attributeName);
        addInvocationAttribute(attributeName, attributeValue);
    }

    /**
     * Check whether the given attribute name exists in the invocaiton context.
     *
     * @param attributeName key
     */
    public boolean containsInvocationAttribute(String attributeName) {
        return getAttributes().containsKey(attributeName);
    }

    /**
     * Get a single value to the invocation attribute with the given name and index.
     *
     * Returns the value with corresponding key and value index.
     * Null if key not found or index out of bound.
     *
     * @param attributeName key
     * @param index item index on the value list
     */
    public String getInvocationAttribute(String attributeName, int index) {
        List<String> attributes = getInvocationAttribute(attributeName);
        if (attributes == null || attributes.size() <= index) {
            return null;
        }

        return attributes.get(index);
    }

    /**
     * Get the value list to the invocation attribute
     *
     * Returns the value list with corresponding key.
     * Null if key not found.
     *
     * @param attributeName key
     */
    public List<String> getInvocationAttribute(String attributeName) {
        return getAttributes().get(attributeName);
    }

    /**
     * Get attribute multi map from invocation context/
     *
     * Return a multi map containing keys and value lists.
     */
    public MultiMap<String, String> getAttributes() {
        return mContext.getAttributes();
    }
}