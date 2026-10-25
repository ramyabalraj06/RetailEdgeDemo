package com.demo.retailedge.inventory;

/** Machine-readable API error envelope. */
public record ApiErrorResponseDto(ErrorDetail error) {

	/** Creates an API error response with a stable code and safe message. */
	public static ApiErrorResponseDto of(String code, String message) {
		return new ApiErrorResponseDto(new ErrorDetail(code, message));
	}

	/** Error details returned to API clients. */
	public record ErrorDetail(String code, String message) {
	}
}