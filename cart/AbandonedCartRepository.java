package com.demo.retailedge.cart;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface AbandonedCartRepository extends JpaRepository<AbandonedCart, Long> {

	Optional<AbandonedCart> findByIdAndStoreId(long id, String storeId);

	List<AbandonedCart> findByStoreIdAndCheckoutStatusAndRecoveryStatusAndAbandonmentTimestampIsNullAndLastActivityAtLessThanEqualAndIdGreaterThanOrderByIdAsc(
			String storeId, CheckoutStatus checkoutStatus, RecoveryStatus recoveryStatus,
			Instant cutoff, long cursor, Pageable limit);

	Optional<AbandonedCart> findFirstByStoreIdAndCustomerIdAndCheckoutStatusAndRecoveryStatusInAndLastActivityAtLessThanEqualOrderByLastActivityAtDescIdDesc(
			String storeId, String customerId, CheckoutStatus checkoutStatus,
			Collection<RecoveryStatus> recoveryStatuses, Instant cutoff);

	List<AbandonedCart> findByStoreIdAndCustomerIdAndRecoveryStatusAndIdNot(
			String storeId, String customerId, RecoveryStatus recoveryStatus, long excludedCartId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@org.springframework.data.jpa.repository.Query("select cart from AbandonedCart cart where cart.id = :id and cart.storeId = :storeId")
	Optional<AbandonedCart> findByIdAndStoreIdForUpdate(@Param("id") long id, @Param("storeId") String storeId);

	List<AbandonedCart> findByStoreIdAndRecoveryStatusAndRecoveryTimestampLessThanAndIdGreaterThanOrderByIdAsc(
			String storeId, RecoveryStatus recoveryStatus, Instant deadline, long cursor, Pageable limit);

	List<String> findDistinctStoreIdByRecoveryStatusAndRecoveryTimestampLessThan(
			RecoveryStatus recoveryStatus, Instant deadline);
}