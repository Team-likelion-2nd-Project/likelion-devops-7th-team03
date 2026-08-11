package com.example.management.links.exception;

/** 링크 생성 요청값이 유효하지 않을 때 사용한다. HTTP 예외 매핑은 Controller 구현 시 추가한다. */
public class InvalidLinkRequestException extends RuntimeException {

    public InvalidLinkRequestException(String message) {
        super(message);
    }
}
