package org.spongepowered.downloads.graphql.api;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.Method;
import org.taymyr.lagom.javadsl.openapi.OpenAPIService;
import org.taymyr.lagom.javadsl.openapi.OpenAPIUtils;

import java.util.Map;

public interface GraphQLService extends OpenAPIService {

    ServiceCall<String, Map<String, Object>> graphql();

    ServiceCall<NotUsed, Map<String, Object>> graphqlByGet(final String queryJson);

    @Override
    default Descriptor descriptor() {
        return OpenAPIUtils.withOpenAPI(Service.named("graphql")
            .withCalls(
                // GraphQL effectively will be on the path, and accept any json blob
                Service.restCall(Method.POST, "/graphql", this::graphql),
                Service.restCall(Method.GET, "/graphql", this::graphqlByGet)
            )
            .withAutoAcl(true)
        );
    }
}
