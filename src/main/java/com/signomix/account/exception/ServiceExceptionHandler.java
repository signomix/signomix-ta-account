package com.signomix.account.exception;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ServiceExceptionHandler implements ExceptionMapper<ServiceException> {

    @ConfigProperty(name ="signomix.exception.api.param.missing")
    String missingParameterException;

    @ConfigProperty(name ="signomix.exception.api.unauthorized")
    String userNotAuthorizedException;



    @Override
    public Response toResponse(ServiceException e) {

        switch(e.getMessage()){
            case "signomix.exception.api.param.missing":
                return Response.status(Response.Status.BAD_REQUEST).entity(new ErrorMessage(e.getMessage()))
                        .build();
            case "signomix.exception.api.unauthorized":
                return Response.status(Response.Status.FORBIDDEN).entity(new ErrorMessage(e.getMessage()))
                        .build();
            default:
                return Response.status(Response.Status.BAD_REQUEST).entity(new ErrorMessage(e.getMessage()))
                        .build();
        }
    }
}
