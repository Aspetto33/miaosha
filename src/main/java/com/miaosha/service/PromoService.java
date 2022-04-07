package com.miaosha.service;

import com.miaosha.service.model.PromoModel;

public interface PromoService {

    //根据itemid获取即将开始或者已经开始即将结束的商品活动
    PromoModel getPromoByItemId(Integer itemId);
}
