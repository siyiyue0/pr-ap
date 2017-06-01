/*
 *   Copyright (C) 2014-2016 www.kequandian.net
 *
 *    The program may be used and/or copied only with the written permission
 *    from www.kequandian.net or in accordance with the terms and
 *    conditions stipulated in the agreement/contract under which the program
 *    has been supplied.
 *
 *    All rights reserved.
 *
 */

package com.jfeat.product.api;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jfeat.core.RestController;
import com.jfeat.product.model.CarryMode;
import com.jfeat.product.service.CarriageCalcResult;
import com.jfeat.product.service.PostageService;
import com.jfeat.product.service.ProductPurchasing;
import com.jfinal.aop.Enhancer;
import com.jfinal.ext.route.ControllerBind;
import com.jfinal.kit.StrKit;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Created by jackyhuang on 16/8/31.
 */
@ControllerBind(controllerKey = "/rest/product_carriage")
public class ProductCarriageController extends RestController {

    private PostageService postageService = Enhancer.enhance(PostageService.class);

    /**
     * POST /rest/product_carriage
     {
        "province": "广东",
        "city": "广州",
        "data":[
            {
                "fare_id": 1,
                "price": 23.20,
                "quantity": 4,
                "weight": 500,
                "bulk": 100
            }
        ]
     }

     Return: carriage - 运费; delta - 距离满包邮的额
     {
        "status_code": 1,
        "data": {
            "carriage": 20.00,
            "delta": -20.00
        }
     }
     */
    public void save() {
        Map<String, Object> map = convertPostJsonToMap();
        String province = (String) map.get("province");
        String city = (String) map.get("city");
        List<Map<String, Object>> data = (List<Map<String, Object>>) map.get("data");
        if (StrKit.isBlank(province) || StrKit.isBlank(city) || data == null) {
            renderFailure("invalid.input");
            return;
        }
        List<ProductPurchasing> productPurchasings = Lists.newArrayList();
        for (Map<String, Object> dataMap : data) {
            Integer fareId = (Integer) dataMap.get("fare_id");
            Integer quantity = (Integer) dataMap.get("quantity");
            BigDecimal price = convertPrice(dataMap.get("price"));
            Integer weight = (Integer) dataMap.get("weight");
            Integer bulk = (Integer) dataMap.get("bulk");
            ProductPurchasing productPurchasing = new ProductPurchasing(fareId, quantity, price, weight, bulk);
            productPurchasings.add(productPurchasing);
        }
        CarriageCalcResult carriageCalcResult = postageService.calculate(productPurchasings, getRegion(province, city), CarryMode.CarryWay.EXPRESS);
        Map<String, Object> result = Maps.newHashMap();
        result.put("carriage", carriageCalcResult.getResult());
        result.put("delta", carriageCalcResult.getDelta());
        result.put("message", carriageCalcResult.getMessage());
        renderSuccess(result);
    }

    private BigDecimal convertPrice(Object price) {
        try {
            return BigDecimal.valueOf((Double) price);
        } catch (ClassCastException ex) {
            return BigDecimal.valueOf((Integer) price);
        }
    }

    private String getRegion(String province, String city) {
        return province + "-" + city;
    }
}
