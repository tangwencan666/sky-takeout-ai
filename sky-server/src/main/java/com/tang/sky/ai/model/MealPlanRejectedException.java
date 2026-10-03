package com.tang.sky.ai.model;

import com.tang.sky.common.BusinessException;
import com.tang.sky.common.ErrorCode;

/** 可审计的业务拒绝原因，不携带模型原文或完整草案。 */
public final class MealPlanRejectedException extends BusinessException {
    public enum Reason {
        DRAFT_MISSING, REASON_INVALID, SELECTION_EMPTY, SELECTION_TOO_LARGE,
        DISH_ID_NULL, DISH_ID_DUPLICATE, DISH_ID_OUTSIDE_CANDIDATES, DISH_UNAVAILABLE, OVER_BUDGET
    }
    private final Reason reason;
    public MealPlanRejectedException(Reason reason) {
        super(ErrorCode.CONFLICT, switch(reason) {
            case DRAFT_MISSING -> "未生成有效的配餐方案，请重新生成";
            case REASON_INVALID -> "配餐推荐说明不完整或过长，请重新生成";
            case SELECTION_EMPTY -> "配餐方案未选择任何菜品，请调整条件后重新生成";
            case SELECTION_TOO_LARGE -> "一次配餐最多选择10道菜品，请重新生成";
            case DISH_UNAVAILABLE -> "推荐菜品当前不可售，请重新选择";
            case OVER_BUDGET -> "推荐超过预算，请重新生成";
            default -> "推荐包含无效或重复菜品，请重新生成";
        });
        this.reason = reason;
    }
    public Reason reason() { return reason; }
}
