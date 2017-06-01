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
package com.jfeat.product.controller;

import com.github.abel533.echarts.code.Orient;
import com.github.abel533.echarts.code.Trigger;
import com.github.abel533.echarts.code.X;
import com.github.abel533.echarts.code.Y;
import com.github.abel533.echarts.json.GsonOption;
import com.github.abel533.echarts.series.Pie;
import com.jfeat.config.model.Config;
import com.jfeat.core.BaseController;
import com.jfeat.core.PhotoGalleryConstants;
import com.jfeat.core.UploadedFile;
import com.jfeat.flash.Flash;
import com.jfeat.kit.qiniu.QiniuKit;
import com.jfeat.product.model.*;
import com.jfeat.product.service.ProductService;
import com.jfeat.product.util.UploadFileComparator;
import com.jfeat.ui.model.Widget;
import com.jfinal.aop.Before;
import com.jfinal.aop.Enhancer;
import com.jfinal.ext.plugin.shiro.ShiroMethod;
import com.jfinal.ext.plugin.upload.filerenamepolicy.CustomParentDirFileRenamePolicy;
import com.jfinal.ext.plugin.upload.filerenamepolicy.NamePolicy;
import com.jfinal.kit.Ret;
import com.jfinal.plugin.activerecord.Record;
import com.jfinal.plugin.activerecord.tx.Tx;
import com.jfinal.upload.UploadFile;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;

import java.util.*;

public class ProductController extends BaseController {

    private ProductService productService = Enhancer.enhance(ProductService.class);

    @Before({Flash.class})
    public void index() {
        Integer pageNumber = getParaToInt("pageNumber", 1);
        Integer pageSize = getParaToInt("pageSize", 30);
        String name = getPara("name");
        String status = getPara("status");
        Integer categoryId = getParaToInt("categoryId");
        Integer promoted = getParaToInt("promoted");
        Integer zone = getParaToInt("zone");
        Integer purchaseStrategyId = getParaToInt("purchaseStrategyId");
        String barCode = getPara("barCode");
        String storeLocation = getPara("storeLocation");

        setAttr("products", productService.paginateProducts(pageNumber, pageSize, name, status, categoryId, promoted, zone, purchaseStrategyId, barCode, storeLocation));
        setAttr("statuses", Product.Status.values());
        List<ProductCategory> productCategories = productService.getProductCategories();
        setAttr("productCategories", productCategories);
        setAttr("purchaseStrategies", ProductPurchaseStrategy.dao.findAll());
        keepPara();
    }

    public void stockBalanceLimited() {
        String status = getPara("status");
        Integer limit = getParaToInt("limit", 0);
        setAttr("products", Product.dao.findStockBalanceLimited(status, limit));
        setAttr("statuses", Product.Status.values());
        setAttr("limit", limit);
        keepPara();
    }

    @RequiresPermissions("product.edit")
    public void edit() {
        Product product = Product.dao.findById(getParaToInt());
        if (product == null) {
            renderError(404);
            return;
        }

        setAttr("product", product);
        setAttr("statuses", Product.Status.values());
        List<ProductCategory> productCategories = productService.getProductCategories();
        setAttr("productCategories", productCategories);
        Config pointExchangeRate = Config.dao.findByKey("mall.point_exchange_rate");
        if (pointExchangeRate != null) {
            setAttr("pointExchangeRate", pointExchangeRate.getValueToInt());
        }

        setAttr("fareTemplates", FareTemplate.dao.findAll());
        setAttr("purchaseStrategies", ProductPurchaseStrategy.dao.findAll());
        setAttr("productBrands", ProductBrand.dao.findAll());
        keepPara();
    }

    @RequiresPermissions("product.edit")
    @Before({Tx.class})
    public void update() {
        List<UploadFile> uploadFiles = getUploadFiles();

        List<UploadFile> newCoverFiles = new ArrayList<>();
        Map<Integer, String> updatedCovers = new HashMap<>();
        for (UploadFile uploadFile : uploadFiles) {
            if (uploadFile.getParameterName().startsWith("new-cover-")) {
                newCoverFiles.add(uploadFile);
            } else if (uploadFile.getParameterName().startsWith("update-cover-")) {
                Integer id = Integer.parseInt(uploadFile.getParameterName().split("update-cover-")[1]);
                String url = getUploadFileUrl(uploadFile);
                if (url != null) {
                    updatedCovers.put(id, url);
                }
            }
        }
        Collections.sort(newCoverFiles, new UploadFileComparator("new-cover-"));
        List<String> newCovers = new ArrayList<>();
        for (UploadFile uploadFile : newCoverFiles) {
            String url = getUploadFileUrl(uploadFile);
            if (url != null) {
                newCovers.add(url);
            }
        }

        Integer[] unchangedCoverIds = getParaValuesToInt("cover-id");
        String description = getPara("description");
        Product product = getModel(Product.class);
        if (product.getBrandId() == null) {
            product.setBrandId(null);
        }
        List<ProductSpecification> specifications = getModels(ProductSpecification.class);
        Ret ret = productService.updateProduct(product, newCovers, updatedCovers, unchangedCoverIds, description, specifications);

        List<ProductProperty> productProperties = getModels(ProductProperty.class);
        if (productProperties.size() == 0) {
            ProductProperty.dao.deleteByProductId(product.getId());
        }
        boolean oldPropertiesRemoved = false;
        for (ProductProperty p : productProperties) {
            if (p.getId() != null) {
                p.update();
            } else {
                //更换了产品的类别, 对应的属性也要变
                if (!oldPropertiesRemoved) {
                    ProductProperty.dao.deleteByProductId(product.getId());
                }
                p.setProductId(product.getId());
                p.save();
                oldPropertiesRemoved = true;
            }
        }

        //update purchase strategy
        Integer strategyId = getParaToInt("purchase_strategy_id");
        if (strategyId != null) {
            ProductPurchaseStrategy.dao.updateProductStrategy(product.getId(), strategyId);
        }

        setFlash("message", getRes().get(ret.get(ProductService.MESSAGE).toString()));

        String returnUrl = getPara("returnUrl", "/product");
        redirect(urlDecode(returnUrl));
    }

    @RequiresPermissions("product.add")
    public void add() {
        List<ProductCategory> productCategories = productService.getProductCategories();
        setAttr("productCategories", productCategories);
        if (productCategories.size() == 0) {
            setFlash("message", getRes().get("product.category_is_empty"));
            redirect("/product");
            return;
        }
        Config pointExchangeRate = Config.dao.findByKey("mall.point_exchange_rate");
        if (pointExchangeRate != null) {
            setAttr("pointExchangeRate", pointExchangeRate.getValueToInt());
        }

        setAttr("fareTemplates", FareTemplate.dao.findAll());
        setAttr("purchaseStrategies", ProductPurchaseStrategy.dao.findAll());
        setAttr("productBrands", ProductBrand.dao.findAll());
        keepPara();
    }

    @Before({Tx.class})
    @RequiresPermissions("product.add")
    public void save() {
        List<UploadFile> uploadFiles = getUploadFiles();
        Collections.sort(uploadFiles, new UploadFileComparator("cover-"));
        List<String> covers = new ArrayList<>();
        for (UploadFile uploadFile : uploadFiles) {
            String url = getUploadFileUrl(uploadFile);
            if (url != null) {
                covers.add(url);
            }
        }

        Product product = getModel(Product.class);
        String description = getPara("description");
        List<ProductSpecification> specifications = getModels(ProductSpecification.class);
        Ret ret = productService.createProduct(product, covers, description, specifications);

        List<ProductProperty> productProperties = getModels(ProductProperty.class);
        for (ProductProperty p : productProperties) {
            p.setProductId(product.getId());
            p.save();
        }

        //update purchase stategy
        Integer strategyId = getParaToInt("purchase_strategy_id");
        if (strategyId != null) {
            ProductPurchaseStrategy.dao.updateProductStrategy(product.getId(), strategyId);
        }

        setFlash("message", getRes().get(ret.get(ProductService.MESSAGE).toString()));
        String returnUrl = getPara("returnUrl", "/product");
        redirect(urlDecode(returnUrl));
    }

    @RequiresPermissions("product.delete")
    public void delete() {
        Ret ret = productService.deleteProduct(getParaToInt());
        setFlash("message", getRes().get(ret.get(ProductService.MESSAGE).toString()));
        String returnUrl = getPara("returnUrl", "/product");
        redirect(urlDecode(returnUrl));
    }

    @RequiresPermissions("product.approve")
    public void approve() {
        changeStatus(new Product.Status[]{Product.Status.PENDING_APPROVAL}, Product.Status.APPROVED);
        String returnUrl = getPara("returnUrl", "/product");
        redirect(urlDecode(returnUrl));
    }

    @RequiresPermissions("product.edit")
    public void publish() {
        Product.Status targetStatus = Product.Status.PENDING_APPROVAL;
        if (ShiroMethod.hasPermission("product.direct_publish")) {
            targetStatus = Product.Status.ONSELL;
        }
        changeStatus(new Product.Status[]{Product.Status.DRAFT}, targetStatus);
        String returnUrl = getPara("returnUrl", "/product");
        redirect(urlDecode(returnUrl));
    }

    @RequiresPermissions("product.edit")
    public void onsell() {
        changeStatus(new Product.Status[]{Product.Status.APPROVED, Product.Status.OFFSELL}, Product.Status.ONSELL);
        String returnUrl = getPara("returnUrl", "/product");
        redirect(returnUrl);
    }

    @RequiresPermissions("product.edit")
    public void offsell() {
        changeStatus(new Product.Status[]{Product.Status.ONSELL}, Product.Status.OFFSELL);
        String returnUrl = getPara("returnUrl", "/product");
        redirect(urlDecode(returnUrl));
    }

    public void widget() {
        //record : status, count
        List<Record> productStatuses = Product.dao.calcStatusCount();
        setAttr("productStatuses", productStatuses);
        List<String> statuses = new LinkedList<>();
        List<Map<String, Object>> dataList = new ArrayList<>();
        for (Product.Status status : Product.Status.values()) {
            String statusName = getRes().get("product.status." + status.toString().toLowerCase());
            statuses.add(statusName);

            long value = 0;
            for (Record record : productStatuses) {
                if (status.toString().equals(record.getStr("status"))) {
                    value = record.getLong("count");
                    break;
                }
            }

            Map<String, Object> map = new HashMap<>();
            map.put("value", value);
            map.put("name", statusName);
            dataList.add(map);
        }

        GsonOption option = new GsonOption();
        //option.title(getRes().get("product.widget.title"));
        option.tooltip().trigger(Trigger.item).formatter("{a} <br/>{b} : {c} ({d}%)");
        option.legend().orient(Orient.horizontal).x(X.center).y(Y.bottom).data(statuses);
        Pie pie = new Pie();
        pie.name(getRes().get("product.widget.title"));
        pie.radius("50%", "70%");
        pie.setData(dataList);
        pie.label().normal().show(false);
        pie.label().emphasis().show(true);
        option.series(pie);

        setAttr("option", option);
        Widget widget = Widget.dao.findFirstByField(Widget.Fields.NAME.toString(), "product.overview");
        setAttr("productWidgetDisplayName", widget.getDisplayName());
        setAttr("productWidgetName", widget.getName());
    }

    /**
     * ajax
     */
    public void getProductCategoryProperties() {
        Integer categoryId = getParaToInt();
        ProductCategory category = ProductCategory.dao.findById(categoryId);
        setAttr("category", category);
        render("_category_properties.html");
    }

    private void changeStatus(Product.Status[] fromStatus, Product.Status toStatus) {
        Product product = Product.dao.findById(getParaToInt());
        if (product == null) {
            renderError(404);
            return;
        }

        for (Product.Status status : fromStatus) {
            if (Product.Status.valueOf(product.getStatus()) == status) {
                product.setStatus(toStatus.toString());
                product.update();
                setFlash("message", getRes().get("product.status.success"));
                return;
            }
        }
    }

    private String saveToQiniu(UploadFile uploadFile) {
        String url = QiniuKit.me().upload(uploadFile.getFile().getAbsolutePath());
        if (url != null) {
            logger.debug("deleted after saved to qiniu.");
            uploadFile.getFile().delete();
            return QiniuKit.me().getFullUrl(url);
        }
        return null;
    }

    private List<UploadFile> getUploadFiles() {
        if (QiniuKit.me().isInited()) {
            return getFiles(QiniuKit.me().getTmpdir());
        }
        String subDir = productService.getProductUploadDir();
        CustomParentDirFileRenamePolicy policy = new CustomParentDirFileRenamePolicy(subDir, NamePolicy.RANDOM_NAME);
        return getFiles(PhotoGalleryConstants.me().getUploadPath(), policy);
    }

    private String getUploadFileUrl(UploadFile uploadFile) {
        if (QiniuKit.me().isInited()) {
            return saveToQiniu(uploadFile);
        }
        return UploadedFile.buildUrl(uploadFile, productService.getProductUploadDir());
    }

    private String urlDecode(String url) {
        return StringEscapeUtils.unescapeHtml4(url);
    }
}
