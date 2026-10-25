package com.demo.retailedge.cart;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

final class MutableTestClock extends Clock {

	private final AtomicReference<Instant> instant;
	private final ZoneId zone;

	MutableTestClock(Instant initialInstant) {
		this(new AtomicReference<>(initialInstant), ZoneId.of("UTC"));
	}

	private MutableTestClock(AtomicReference<Instant> instant, ZoneId zone) {
		this.instant = instant;
		this.zone = zone;
	}

	void setInstant(Instant value) {
		instant.set(value);
	}

	@Override
	public ZoneId getZone() {
		return zone;
	}

	@Override
	public Clock withZone(ZoneId requestedZone) {
		return new MutableTestClock(instant, requestedZone);
	}

	@Override
	public Instant instant() {
		return instant.get();
	}
}