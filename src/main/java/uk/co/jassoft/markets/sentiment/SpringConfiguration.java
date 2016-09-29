package uk.co.jassoft.markets.sentiment;

import uk.co.jassoft.markets.BaseSpringConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Created by jonshaw on 13/07/15.
 */
@Configuration
@ComponentScan("uk.co.jassoft.markets.sentiment")
public class SpringConfiguration extends BaseSpringConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(SpringConfiguration.class);

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    public static void main(String[] args) throws Exception {
        ConfigurableApplicationContext context = SpringApplication.run(SpringConfiguration.class, args);

        LOG.info("Running Sentiment Args [{}]", args);

        context.getBean(StorySentimentListener.class).sentimentAnalyse(args[0]);

        LOG.info("Finished Running Sentiment Args [{}]", args);

        context.close();
        System.exit(0);
    }
}
