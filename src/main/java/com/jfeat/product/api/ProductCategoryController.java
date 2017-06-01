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
import com.jfeat.core.RestController;
import com.jfeat.product.model.Product;
import com.jfeat.product.model.ProductCategory;
import com.jfeat.product.util.CategoryPromotedCarousel;
import com.jfinal.ext.route.ControllerBind;
import com.jfinal.kit.StrKit;
import com.jfinal.plugin.activerecord.Page;

import java.util.List;

@ControllerBind(controllerKey = "/rest/product_category")
public class ProductCategoryController extends RestController {

    /**
     * get /rest/product_category?promoted=true&pageNumber=1
     * if promoted parameter is specified, query the promoted products under the category.
     * else just return the categories.
     */
    public void index() {
        List<ProductCategory> productCategories = ProductCategory.dao.findAllRecursively();
        if (StrKit.isBlank(getPara("promoted"))) {
            renderSuccess(productCategories);
            return;
        }

        List<ProductCategory> promotedCategories = Lists.newArrayList();
        for (ProductCategory category : productCategories) {
            if (category.getPromoted().equals(ProductCategory.PROMOTED)) {
                promotedCategories.add(category);
            }
            else {
                //如果一级类别不是promoted, 那么查看它的二级类别是否promoted
                for (ProductCategory child : category.getChildren()) {
                    if (child.getPromoted().equals(ProductCategory.PROMOTED)) {
                        promotedCategories.add(child);
                    }
                }
            }
        }

        String[] orderByList = getParaValues("orderBy");
        String[] orderByDescList = getParaValues("orderByDesc");
        for (ProductCategory category : promotedCategories) {
            Integer pageSize = getParaToInt("pageSize", category.getPromotedProductCount());
            Integer pageNumber = getParaToInt("pageNumber", CategoryPromotedCarousel.me().nextCarouselPageNumber(category.getId(), pageSize));
            List<Integer> categoryIds = Lists.newArrayList();
            categoryMapping(category, categoryIds);
            Page<Product> page = Product.dao.paginateByPromoted(pageNumber, pageSize, orderByList, orderByDescList, categoryIds.toArray(new Integer[0]));
            CategoryPromotedCarousel.me().updateCarousel(category.getId(), page.getTotalRow(), page.getTotalPage());
            category.put("products", page.getList());
        }

        renderSuccess(promotedCategories);
    }

    private void categoryMapping(ProductCategory category, List<Integer> categoryIds) {
        if (categoryIds == null) {
            throw new IllegalArgumentException("categoryIds cannot be null.");
        }
        if (category != null) {
            categoryIds.add(category.getId());
            for (ProductCategory c : category.getChildren()) {
                categoryMapping(c, categoryIds);
            }
        }
    }

    /**
     * get /rest/product_category/id?promoted=true
     */
    public void show() {
        ProductCategory category = ProductCategory.dao.findById(getParaToInt());
        if (category == null) {
            renderFailure("product_category.is.null");
            return;
        }
        Integer pageNumber = getParaToInt("pageNumber", 1);
        Integer pageSize = getParaToInt("pageSize", 50);
        String[] orderByList = getParaValues("orderBy");
        String[] orderByDescList = getParaValues("orderByDesc");
        List<Product> products;
        if (StrKit.notBlank(getPara("promoted"))) {
            List<Integer> categoryIds = Lists.newArrayList();
            categoryMapping(category, categoryIds);
            products = Product.dao.paginateByPromoted(pageNumber, pageSize,
                    orderByList, orderByDescList,
                    categoryIds.toArray(new Integer[0])).getList();
        }
        else {
            products = Product.dao.paginate(pageNumber, pageSize,
                    null, Product.Status.ONSELL.toString(), category.getId(), null ,null,
                    orderByList, orderByDescList).getList();
        }
        category.put("products", products);
        renderSuccess(category);
    }

}
