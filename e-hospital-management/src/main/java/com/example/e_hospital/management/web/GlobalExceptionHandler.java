package com.example.e_hospital.management.web;

import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.validation.BindException;

@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(AccessDeniedException.class) @ResponseStatus(HttpStatus.FORBIDDEN)
    public String denied() { return "error/403"; }
    @ExceptionHandler({RecordNotFoundException.class, NoResourceFoundException.class, NoHandlerFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND) public String missing() { return "error/404"; }
    @ExceptionHandler({BindException.class, IllegalArgumentException.class, org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST) public String invalid() { return "error/400"; }
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class) @ResponseStatus(HttpStatus.CONFLICT)
    public String conflict() { return "error/409"; }
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public String unsupportedMethod(org.springframework.web.HttpRequestMethodNotSupportedException exception,
            jakarta.servlet.http.HttpServletResponse response) {
        if (exception.getSupportedMethods() != null) response.setHeader("Allow", String.join(", ", exception.getSupportedMethods()));
        return "error/405";
    }
    @ExceptionHandler(Exception.class) @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String unexpected(Exception exception, Model model) {
        String reference = java.util.UUID.randomUUID().toString();
        String location = exception.getStackTrace().length == 0 ? "unavailable" : exception.getStackTrace()[0].toString();
        LoggerFactory.getLogger(getClass()).error("Request failure {} type={} location={}", reference, exception.getClass().getName(), location);
        model.addAttribute("errorReference", reference); return "error/500";
    }
}
