package com.example.edps.domain.product.repository;

import com.example.edps.domain.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Page<Product> findByNameContainingIgnoreCase(String name, Pageable pageable);

    List<Product> findAllByIdIn(List<Long> ids);

    /**
     * 재고 차감
     * - 재고가 충분할 때만(재고 >= 요청수량) 차감되도록 조건 포함
     * - 동시성 문제 방지
     * @param productId
     * @param requested 구매 수량
     * @return 업데이트 된 row 수
     */
    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock - :requested WHERE p.id = :productId AND p.stock >= :requested")
    int reduceStock(@Param("productId") Long productId, @Param("requested") int requested);

    /**
     * 재고 증가 쿼리 (주문 실패 시 재고 롤백)
     * 재고 증가에는 특별한 조건이 없으므로 단순 업데이트
     *
     * @param productId
     * @param requested
     * @return 업데이트 된 row 수
     */
    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock + :requested WHERE p.id = :productId")
    int increaseStock(@Param("productId") Long productId, @Param("requested") int requested);

}
