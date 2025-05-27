package com.example.demo.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.demo.dto.CalculationResultDto;
import com.example.demo.dto.CartItemInput;
import com.example.demo.dto.ShoppingCartInput;
import com.example.demo.model.Coupon;
import com.example.demo.model.Product;
import com.example.demo.repository.CouponRepository;
import com.example.demo.repository.ProductRepository;

/**
 * 購物車服務負責根據輸入的購物車資料計算總價和折扣。
 */
@Service
public class CartService {

    private static final Logger log = LoggerFactory.getLogger(CartService.class);

    private final ProductRepository productRepository;
    private final CouponRepository couponRepository;

    public CartService(ProductRepository productRepository, CouponRepository couponRepository) {
        this.productRepository = productRepository;
        this.couponRepository = couponRepository;
    }

    /**
     * 根據輸入的 {@link ShoppingCartInput} 計算購物車的總價和折扣。
     * <p>
     * 此方法僅允許擇一使用優惠券。若收到多個代碼，只套用第一個有效代碼。
     *
     * @param cartInput 包含購物車項目、數量和優惠券代碼列表的輸入物件。
     * @return {@link CalculationResultDto} 包含原始總價、折扣後總價、實際折扣金額以及所有套用的優惠券列表。
     */
    public CalculationResultDto calculateCartPrice(ShoppingCartInput cartInput) {
        Integer rawTotalPrice = calculateRawTotalPrice(cartInput.items());

        List<Coupon> appliedCoupons = new ArrayList<>();
        Integer totalDiscountAmountFromCoupons = applyCoupons(cartInput.couponCodes(), appliedCoupons);

        // 計算折扣後總價，並確保不為負數
        Integer finalPrice = rawTotalPrice - totalDiscountAmountFromCoupons;
        if (finalPrice < 0) {
            log.warn("折扣後總價計算結果小於 0，將其校正為 0。原始總價:{}, 折扣總額:{}",
                    rawTotalPrice, totalDiscountAmountFromCoupons);
            finalPrice = 0;
        }

        Integer effectiveTotalDiscount = rawTotalPrice - finalPrice;

        log.info("計算完成。原始總價: {}, 折扣後總價: {}, 優惠券聲稱總折扣: {}, 實際總折扣: {}",
                rawTotalPrice, finalPrice, totalDiscountAmountFromCoupons, effectiveTotalDiscount);

        return new CalculationResultDto(rawTotalPrice, finalPrice, effectiveTotalDiscount, appliedCoupons);
    }

    /**
     * 計算購物車中所有商品的原始總價。
     *
     * @param items 購物車中的商品項目列表。
     * @return 原始總價。
     */
    private Integer calculateRawTotalPrice(List<CartItemInput> items) {
        Integer currentRawTotalPrice = 0;
        if (items != null) {
            for (CartItemInput itemInput : items) {
                Optional<Product> optionalProduct = productRepository.findById(itemInput.productId());
                if (optionalProduct.isPresent()) {
                    Product product = optionalProduct.get();
                    currentRawTotalPrice += product.getPrice() * itemInput.quantity();
                } else {
                    log.warn("計算時找不到產品 ID: {}。此商品將不列入計算。", itemInput.productId());
                }
            }
        }
        return currentRawTotalPrice;
    }

    /**
     * 處理並套用提供的優惠券代碼。
     *
     * @param couponCodes    使用者提供的優惠券代碼列表。
     * @param appliedCoupons 用於收集實際套用的優惠券實例列表 (此列表會被此方法修改)。
     * @return 從所有套用的優惠券中獲得的總折扣金額。
     */
    private Integer applyCoupons(List<String> couponCodes, List<Coupon> appliedCoupons) {
        if (couponCodes == null || couponCodes.isEmpty()) {
            return 0;
        }

        if (couponCodes.size() > 1) {
            log.warn("收到多於一張優惠券代碼，僅套用第一張: {}", couponCodes);
        }

        String couponCode = couponCodes.get(0);
        if (couponCode == null || couponCode.trim().isEmpty()) {
            return 0;
        }

        Optional<Coupon> optionalCoupon = couponRepository.findByCode(couponCode);
        if (optionalCoupon.isPresent()) {
            Coupon coupon = optionalCoupon.get();
            appliedCoupons.add(coupon);
            log.debug("套用優惠券 '{}', 折抵金額: {}", coupon.getDescription(), coupon.getDiscountAmount());
            return coupon.getDiscountAmount();
        } else {
            log.warn("計算時找不到優惠券代碼: {}。此券將不被套用。", couponCode);
            return 0;
        }
    }
}