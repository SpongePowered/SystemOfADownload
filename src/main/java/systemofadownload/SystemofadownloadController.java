package systemofadownload;

import akka.Done;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Scheduler;
import akka.actor.typed.javadsl.AskPattern;
import akka.cluster.sharding.typed.ShardingEnvelope;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Inject;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;

@Controller("/systemofadownload")
public class SystemofadownloadController {
    record Command() {}

    private final ActorRef<ShardingEnvelope<Command>> region;
    private final Scheduler scheduler;

    @Inject
    public SystemofadownloadController(
        ActorRef<ShardingEnvelope<Command>> region,
        Scheduler scheduler
    ) {
        this.region = region;
        this.scheduler = scheduler;

    }

    @Get(uri="/", produces="text/plain")
    @Secured(SecurityRule.IS_ANONYMOUS)
    public String index() {
        return "Hello world";
    }

    public static final Duration TIMEOUT = Duration.ofSeconds(10);

    public void someFireForgetMethod(){
        this.region.tell(new ShardingEnvelope<>("foo", new Command()));
    }

    record Foo() {}
    public Mono<Foo> someNeedResponseMethod(){
        CompletionStage<Foo> willBeResponse = AskPattern.ask(
            this.region,
            replyTo -> new ShardingEnvelope<>("entityId", new Command()),
            TIMEOUT,
            scheduler
        );
        return Mono.fromCompletionStage(willBeResponse);
    }
}
