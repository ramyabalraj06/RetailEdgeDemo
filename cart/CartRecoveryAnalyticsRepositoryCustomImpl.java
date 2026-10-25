package com.demo.retailedge.cart;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.springframework.stereotype.Repository;

/** Builds a store-scoped analytics keyset query using JPA Criteria, never raw SQL or offsets. */
@Repository
public class CartRecoveryAnalyticsRepositoryCustomImpl implements CartRecoveryAnalyticsRepositoryCustom {

	@PersistenceContext
	private EntityManager entityManager;

	@Override
	public List<CartRecoveryTransition> findRecoveryTransitions(
			String storeId, Instant from, Instant to, Long afterId, int maxResults) {
		CriteriaBuilder builder = entityManager.getCriteriaBuilder();
		CriteriaQuery<CartRecoveryTransition> query = builder.createQuery(CartRecoveryTransition.class);
		Root<CartRecoveryTransition> transition = query.from(CartRecoveryTransition.class);
		List<Predicate> predicates = new ArrayList<>();
		predicates.add(builder.equal(transition.get("storeId"), storeId));
		predicates.add(builder.equal(transition.get("stateFamily"), "RECOVERY"));
		predicates.add(builder.greaterThanOrEqualTo(transition.get("changedAt"), from));
		predicates.add(builder.lessThan(transition.get("changedAt"), to));
		if (afterId != null) {
			predicates.add(builder.greaterThan(transition.get("id"), afterId));
		}
		query.select(transition).where(predicates.toArray(Predicate[]::new))
				.orderBy(builder.asc(transition.get("id")));
		return entityManager.createQuery(query).setMaxResults(maxResults).getResultList();
	}
}