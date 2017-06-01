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
package com.jfeat.common;

import com.jfeat.config.model.Config;
import com.jfeat.core.JFeatConfig;
import com.jfeat.core.Module;
import com.jfeat.observer.ObserverKit;
import com.jfeat.product.handler.PromotedProductCarouselUpdatedHandler;
import com.jfeat.product.util.CategoryPromotedCarousel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProductApplicationModule extends Module {

    private static Logger logger = LoggerFactory.getLogger(ProductApplicationModule.class);

    public ProductApplicationModule(JFeatConfig jfeatConfig) {
        super(jfeatConfig);
        ProductApplicationModelMapping.mapping(this);

        addXssExcluded("/product");

        // 1. register your controllers
        // addController(YourDefinedController.class);
        addController(com.jfeat.product.controller.ProductCategoryController.class);
        addController(com.jfeat.product.controller.ProductController.class);
        addController(com.jfeat.product.api.ProductController.class);
        addController(com.jfeat.product.api.ProductCategoryController.class);
        addController(com.jfeat.product.api.ProductSearchController.class);
        addController(com.jfeat.product.api.ProductFavoriteController.class);
        addController(com.jfeat.product.api.ProductHitWordController.class);
        addController(com.jfeat.product.api.ProductCarriageController.class);
        addController(com.jfeat.product.controller.FareTemplateController.class);
        addController(com.jfeat.product.controller.PurchaseStrategyController.class);
        addController(com.jfeat.product.controller.ProductHitWordController.class);
        addController(com.jfeat.product.api.ProductPurchaseStrategyController.class);
        addController(com.jfeat.product.controller.ProductBrandController.class);

        // 3. config the module you dependencied.
        // new YouDependenciedModule(jfeatConfig);
        new PcdDomainModule(jfeatConfig);
        new ConfigDomainModule(jfeatConfig);
        new ProductDomainModule(jfeatConfig);
        new IdentityApplicationModule(jfeatConfig);

        ObserverKit.me().register(Config.class, Config.EVENT_UPDATE, PromotedProductCarouselUpdatedHandler.class);
    }

    @Override
    public void afterJFinalStart() {
        super.afterJFinalStart();
        Config config = Config.dao.findByKey("mall.promoted_product_carousel");
        if (config != null && config.getValueToInt() != null) {
            int timeout = config.getValueToInt() > 30 ? config.getValueToInt() : 30;
            CategoryPromotedCarousel.me().setCarouselTimeout(timeout * 60 * 1000);
            logger.info("Category Promoted Carousel timeout set as {} minutes.", timeout);
        }
    }
}
