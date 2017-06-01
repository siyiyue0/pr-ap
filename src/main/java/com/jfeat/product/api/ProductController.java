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

import com.jfeat.core.RestController;
import com.jfeat.identity.authc.ShiroUser;
import com.jfeat.identity.model.User;
import com.jfeat.identity.interceptor.CurrentUserInterceptor;
import com.jfeat.product.interceptor.ProductViewCountInterceptor;
import com.jfeat.product.model.*;
import com.jfeat.product.service.ProductService;
import com.jfinal.aop.Before;
import com.jfinal.aop.Enhancer;
import com.jfinal.ext.route.ControllerBind;
import com.jfinal.plugin.activerecord.tx.Tx;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.subject.Subject;

import java.util.ArrayList;
import java.util.List;

@ControllerBind(controllerKey = "/rest/product")
public class ProductController extends RestController {

    /**
     * GET /rest/product?zone=1
     * query the promoted products if no zone provided.
     * else query the zone products.
     */
    public void index() {
        Integer pageNumber = getParaToInt("pageNumber", 1);
        Integer pageSize = getParaToInt("pageSize", 50);
        Integer zone = getParaToInt("zone");
        String[] orderByList = getParaValues("orderBy");
        String[] orderByDescList = getParaValues("orderByDesc");
        Integer promoted = zone == null ? Product.Promoted.YES.getValue() : null;
        List<Product> promotedProducts = Product.dao.paginate(pageNumber, pageSize,
                    null, Product.Status.ONSELL.toString(), null, promoted, zone, orderByList, orderByDescList)
                .getList();
        renderSuccess(promotedProducts);
    }

    @Before(ProductViewCountInterceptor.class)
    public void show() {
        Product product = Product.dao.findById(getParaToInt());
        if (product == null) {
            renderFailure("product.is.null");
            return;
        }
        Product.Status status = Product.Status.valueOf(product.getStatus());
        if (status == Product.Status.OFFSELL) {
            renderFailure("product.is.offsell");
            return;
        }
        if (status != Product.Status.ONSELL) {
            renderFailure("product.invalid.status");
            return;
        }

        FareTemplate fareTemplate = product.getFareTemplate();
        if (fareTemplate != null) {
            fareTemplate.put("carry_modes", fareTemplate.getCarryModes());
            fareTemplate.put("incl_postage_provisoes", fareTemplate.getInclPostageProvisoes());
        }

        List<ProductSpecification> productSpecifications = product.getProductSpecifications();

        String description = null;
        ProductDescription productDescription = product.getProductDescription();
        if (productDescription != null) {
            description = productDescription.getDescription();
        }

        product.put("covers", product.getCovers());
        product.put("properties", product.getProductProperties());
        product.put("specifications", productSpecifications);
        product.put("description", description);
        product.put("fare_template", fareTemplate);
        product.put("purchase_strategy", product.getPurchaseStrategy());

        renderSuccess(product);
    }
}
