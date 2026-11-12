package com.hmood.catalog;

public class CatalogException extends Exception {
    public CatalogException(String message, Throwable cause) {
        super(message, cause);
    }

    public CatalogException(String message) {
        super(message);
    }
}
