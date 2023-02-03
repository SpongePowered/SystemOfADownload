package systemofadownload;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.security.authentication.AuthorizationException;
import io.micronaut.security.authentication.DefaultAuthorizationExceptionHandler;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
@Replaces(DefaultAuthorizationExceptionHandler.class)
public class UnauthorizedHandler extends DefaultAuthorizationExceptionHandler {

    @Override
    public MutableHttpResponse<?> handle(final HttpRequest request, final AuthorizationException exception) {
        return super.handle(request, exception)
            .body(new UnauthorizedError(401, List.of("Unauthorized")));
    }


    @Serdeable
    record UnauthorizedError(
        int code, List<String> errors
    ) {

    }
}
