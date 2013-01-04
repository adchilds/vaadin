/*
 * Copyright 2011 Vaadin Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.vaadin.client.ui.orderedlayout;

import java.util.HashMap;
import java.util.Map;

import com.google.gwt.dom.client.Node;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.regexp.shared.MatchResult;
import com.google.gwt.regexp.shared.RegExp;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.RequiresResize;
import com.google.gwt.user.client.ui.Widget;
import com.vaadin.client.LayoutManager;
import com.vaadin.client.Util;
import com.vaadin.shared.ui.MarginInfo;

/**
 * Base class for ordered layouts
 */
public class VAbstractOrderedLayout extends FlowPanel {

    protected boolean spacing = false;

    /** For internal use only. May be removed or replaced in the future. */
    public boolean vertical = true;

    protected boolean definedHeight = false;

    private Map<Widget, Slot> widgetToSlot = new HashMap<Widget, Slot>();

    private Element expandWrapper;

    private LayoutManager layoutManager;

    /**
     * Keep track of the last allocated expand size to help detecting when it
     * changes.
     */
    private int lastExpandSize = -1;

    public VAbstractOrderedLayout(boolean vertical) {
        this.vertical = vertical;
    }

    /**
     * Add or move a slot to another index.
     * <p>
     * For internal use only. May be removed or replaced in the future.
     * <p>
     * You should note that the index does not refer to the DOM index if
     * spacings are used. If spacings are used then the index will be adjusted
     * to include the spacings when inserted.
     * <p>
     * For instance when using spacing the index converts to DOM index in the
     * following way:
     * 
     * <pre>
     * index : 0 -> DOM index: 0
     * index : 1 -> DOM index: 1
     * index : 2 -> DOM index: 3
     * index : 3 -> DOM index: 5
     * index : 4 -> DOM index: 7
     * </pre>
     * 
     * When using this method never account for spacings.
     * </p>
     * 
     * @param slot
     *            The slot to move or add
     * @param index
     *            The index where the slot should be placed.
     */
    public void addOrMoveSlot(Slot slot, int index) {
        if (slot.getParent() == this) {
            int currentIndex = getWidgetIndex(slot);
            if (index == currentIndex) {
                return;
            }
        }

        insert(slot, index);

        /*
         * We need to confirm spacings are correctly applied after each insert.
         */
        setSpacing(spacing);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void insert(Widget child, Element container, int beforeIndex,
            boolean domInsert) {
        // Validate index; adjust if the widget is already a child of this
        // panel.
        beforeIndex = adjustIndex(child, beforeIndex);

        // Detach new child.
        child.removeFromParent();

        // Logical attach.
        getChildren().insert(child, beforeIndex);

        // Physical attach.
        container = expandWrapper != null ? expandWrapper : getElement();
        if (domInsert) {
            if (spacing) {
                if (beforeIndex != 0) {
                    /*
                     * Since the spacing elements are located at the same DOM
                     * level as the slots we need to take them into account when
                     * calculating the slot position.
                     * 
                     * The spacing elements are always located before the actual
                     * slot except for the first slot which do not have a
                     * spacing element like this
                     * 
                     * |<slot1><spacing2><slot2><spacing3><slot3>...|
                     */
                    beforeIndex = beforeIndex * 2 - 1;
                }
            }
            DOM.insertChild(container, child.getElement(), beforeIndex);
        } else {
            DOM.appendChild(container, child.getElement());
        }

        // Adopt.
        adopt(child);
    }

    /**
     * Remove a slot from the layout
     * 
     * @param widget
     * @return
     */
    public void removeWidget(Widget widget) {
        Slot slot = widgetToSlot.get(widget);
        remove(slot);
        widgetToSlot.remove(widget);
    }

    /**
     * Get the containing slot for a widget. If no slot is found a new slot is
     * created and returned.
     * 
     * @param widget
     *            The widget whose slot you want to get
     * 
     * @return
     */
    public Slot getSlot(Widget widget) {
        Slot slot = widgetToSlot.get(widget);
        if (slot == null) {
            slot = new Slot(this, widget);
            widgetToSlot.put(widget, slot);
        }
        return slot;
    }

    /**
     * Gets a slot based on the widget element. If no slot is found then null is
     * returned.
     * 
     * @param widgetElement
     *            The element of the widget ( Same as getWidget().getElement() )
     * @return
     */
    public Slot getSlot(Element widgetElement) {
        for (Map.Entry<Widget, Slot> entry : widgetToSlot.entrySet()) {
            if (entry.getKey().getElement() == widgetElement) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Set the layout manager for the layout
     * 
     * @param manager
     *            The layout manager to use
     */
    public void setLayoutManager(LayoutManager manager) {
        layoutManager = manager;
    }

    /**
     * Get the layout manager used by this layout
     * 
     */
    public LayoutManager getLayoutManager() {
        return layoutManager;
    }

    /**
     * Deducts the caption position by examining the wrapping element.
     * <p>
     * For internal use only. May be removed or replaced in the future.
     * 
     * @param captionWrap
     *            The wrapping element
     * 
     * @return The caption position
     */
    public CaptionPosition getCaptionPositionFromElement(Element captionWrap) {
        RegExp captionPositionRegexp = RegExp.compile("v-caption-on-(\\S+)");

        // Get caption position from the classname
        MatchResult matcher = captionPositionRegexp.exec(captionWrap
                .getClassName());
        if (matcher == null || matcher.getGroupCount() < 2) {
            return CaptionPosition.TOP;
        }
        String captionClass = matcher.getGroup(1);
        CaptionPosition captionPosition = CaptionPosition.valueOf(
                CaptionPosition.class, captionClass.toUpperCase());
        return captionPosition;
    }

    /**
     * Update the offset off the caption relative to the slot
     * <p>
     * For internal use only. May be removed or replaced in the future.
     * 
     * @param caption
     *            The caption element
     */
    public void updateCaptionOffset(Element caption) {

        Element captionWrap = caption.getParentElement().cast();

        Style captionWrapStyle = captionWrap.getStyle();
        captionWrapStyle.clearPaddingTop();
        captionWrapStyle.clearPaddingRight();
        captionWrapStyle.clearPaddingBottom();
        captionWrapStyle.clearPaddingLeft();

        Style captionStyle = caption.getStyle();
        captionStyle.clearMarginTop();
        captionStyle.clearMarginRight();
        captionStyle.clearMarginBottom();
        captionStyle.clearMarginLeft();

        // Get caption position from the classname
        CaptionPosition captionPosition = getCaptionPositionFromElement(captionWrap);

        if (captionPosition == CaptionPosition.LEFT
                || captionPosition == CaptionPosition.RIGHT) {
            int captionWidth;
            if (layoutManager != null) {
                captionWidth = layoutManager.getOuterWidth(caption)
                        - layoutManager.getMarginWidth(caption);
            } else {
                captionWidth = caption.getOffsetWidth();
            }
            if (captionWidth > 0) {
                if (captionPosition == CaptionPosition.LEFT) {
                    captionWrapStyle.setPaddingLeft(captionWidth, Unit.PX);
                    captionStyle.setMarginLeft(-captionWidth, Unit.PX);
                } else {
                    captionWrapStyle.setPaddingRight(captionWidth, Unit.PX);
                    captionStyle.setMarginRight(-captionWidth, Unit.PX);
                }
            }
        }
        if (captionPosition == CaptionPosition.TOP
                || captionPosition == CaptionPosition.BOTTOM) {
            int captionHeight;
            if (layoutManager != null) {
                captionHeight = layoutManager.getOuterHeight(caption)
                        - layoutManager.getMarginHeight(caption);
            } else {
                captionHeight = caption.getOffsetHeight();
            }
            if (captionHeight > 0) {
                if (captionPosition == CaptionPosition.TOP) {
                    captionWrapStyle.setPaddingTop(captionHeight, Unit.PX);
                    captionStyle.setMarginTop(-captionHeight, Unit.PX);
                } else {
                    captionWrapStyle.setPaddingBottom(captionHeight, Unit.PX);
                    captionStyle.setMarginBottom(-captionHeight, Unit.PX);
                }
            }
        }
    }

    /**
     * Set the margin of the layout
     * 
     * @param marginInfo
     *            The margin information
     */
    public void setMargin(MarginInfo marginInfo) {
        if (marginInfo != null) {
            setStyleName("v-margin-top", marginInfo.hasTop());
            setStyleName("v-margin-right", marginInfo.hasRight());
            setStyleName("v-margin-bottom", marginInfo.hasBottom());
            setStyleName("v-margin-left", marginInfo.hasLeft());
        }
    }

    /**
     * Turn on or off spacing in the layout
     * 
     * @param spacing
     *            True if spacing should be used, false if not
     */
    public void setSpacing(boolean spacing) {
        this.spacing = spacing;
        for (Slot slot : widgetToSlot.values()) {
            if (getWidgetIndex(slot) > 0) {
                slot.setSpacing(spacing);
            } else {
                slot.setSpacing(false);
            }
        }
    }

    /**
     * Assigns relative sizes to the children that should expand based on their
     * expand ratios.
     */
    public void updateExpandedSizes() {
        // Ensure the expand wrapper is in place
        if (expandWrapper == null) {
            expandWrapper = DOM.createDiv();
            expandWrapper.setClassName("v-expand");
            while (getElement().getChildCount() > 0) {
                Node el = getElement().getChild(0);
                expandWrapper.appendChild(el);
            }
            getElement().appendChild(expandWrapper);
        }

        // Sum up expand ratios to get the denominator
        double total = 0;
        for (Slot slot : widgetToSlot.values()) {
            if (slot.getExpandRatio() != 0) {
                total += slot.getExpandRatio();
            } else {
                if (vertical) {
                    slot.getElement().getStyle().clearHeight();
                } else {
                    slot.getElement().getStyle().clearWidth();
                }
            }
            slot.getElement().getStyle().clearMarginLeft();
            slot.getElement().getStyle().clearMarginTop();
        }

        // Give each child its own share
        for (Slot slot : widgetToSlot.values()) {
            if (slot.getExpandRatio() != 0) {
                if (vertical) {
                    slot.setHeight((100 * (slot.getExpandRatio() / total))
                            + "%");
                    if (slot.hasRelativeHeight()) {
                        Util.notifyParentOfSizeChange(this, true);
                    }
                } else {
                    slot.setWidth((100 * (slot.getExpandRatio() / total)) + "%");
                    if (slot.hasRelativeWidth()) {
                        Util.notifyParentOfSizeChange(this, true);
                    }
                }
            }
        }
    }

    /**
     * Removes elements used to expand a slot.
     * <p>
     * For internal use only. May be removed or replaced in the future.
     */
    public void clearExpand() {
        if (expandWrapper != null) {
            lastExpandSize = -1;
            while (expandWrapper.getChildCount() > 0) {
                Element el = expandWrapper.getChild(0).cast();
                getElement().appendChild(el);
                if (vertical) {
                    el.getStyle().clearHeight();
                    el.getStyle().clearMarginTop();
                } else {
                    el.getStyle().clearWidth();
                    el.getStyle().clearMarginLeft();
                }
            }
            expandWrapper.removeFromParent();
            expandWrapper = null;
        }
    }

    /**
     * Updates the expand compensation based on the measured sizes of children
     * without expand.
     */
    public void updateExpandCompensation() {
        boolean isExpanding = false;
        for (Widget slot : getChildren()) {
            if (((Slot) slot).getExpandRatio() != 0) {
                isExpanding = true;
                break;
            }
        }

        if (isExpanding) {
            int totalSize = 0;
            for (Widget w : getChildren()) {
                Slot slot = (Slot) w;
                if (slot.getExpandRatio() == 0) {

                    if (layoutManager != null) {
                        // TODO check caption position
                        if (vertical) {
                            int size = layoutManager.getOuterHeight(slot
                                    .getWidget().getElement())
                                    - layoutManager.getMarginHeight(slot
                                            .getWidget().getElement());
                            if (slot.hasCaption()) {
                                size += layoutManager.getOuterHeight(slot
                                        .getCaptionElement())
                                        - layoutManager.getMarginHeight(slot
                                                .getCaptionElement());
                            }
                            if (size > 0) {
                                totalSize += size;
                            }
                        } else {
                            int max = -1;
                            max = layoutManager.getOuterWidth(slot.getWidget()
                                    .getElement())
                                    - layoutManager.getMarginWidth(slot
                                            .getWidget().getElement());
                            if (slot.hasCaption()) {
                                int max2 = layoutManager.getOuterWidth(slot
                                        .getCaptionElement())
                                        - layoutManager.getMarginWidth(slot
                                                .getCaptionElement());
                                max = Math.max(max, max2);
                            }
                            if (max > 0) {
                                totalSize += max;
                            }
                        }
                    } else {
                        totalSize += vertical ? slot.getOffsetHeight() : slot
                                .getOffsetWidth();
                    }
                }
                // TODO fails in Opera, always returns 0
                int spacingSize = vertical ? slot.getVerticalSpacing() : slot
                        .getHorizontalSpacing();
                if (spacingSize > 0) {
                    totalSize += spacingSize;
                }
            }

            // When we set the margin to the first child, we don't need
            // overflow:hidden in the layout root element, since the wrapper
            // would otherwise be placed outside of the layout root element
            // and block events on elements below it.
            if (vertical) {
                expandWrapper.getStyle().setPaddingTop(totalSize, Unit.PX);
                expandWrapper.getFirstChildElement().getStyle()
                        .setMarginTop(-totalSize, Unit.PX);
            } else {
                expandWrapper.getStyle().setPaddingLeft(totalSize, Unit.PX);
                expandWrapper.getFirstChildElement().getStyle()
                        .setMarginLeft(-totalSize, Unit.PX);
            }

            // Measure expanded children again if their size might have changed
            if (totalSize != lastExpandSize) {
                lastExpandSize = totalSize;
                for (Widget w : getChildren()) {
                    Slot slot = (Slot) w;
                    if (slot.getExpandRatio() != 0) {
                        if (layoutManager != null) {
                            layoutManager.setNeedsMeasure(Util
                                    .findConnectorFor(slot.getWidget()));
                        } else if (slot.getWidget() instanceof RequiresResize) {
                            ((RequiresResize) slot.getWidget()).onResize();
                        }
                    }
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setHeight(String height) {
        super.setHeight(height);
        definedHeight = (height != null && !"".equals(height));
    }

    /**
     * Sets the slots style names. The style names will be prefixed with the
     * v-slot prefix.
     * 
     * @param stylenames
     *            The style names of the slot.
     */
    public void setSlotStyleNames(Widget widget, String... stylenames) {
        Slot slot = getSlot(widget);
        if (slot == null) {
            throw new IllegalArgumentException(
                    "A slot for the widget could not be found. Has the widget been added to the layout?");
        }
        slot.setStyleNames(stylenames);
    }

}