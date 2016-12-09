/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.co.jassoft.markets.sentiment.lingual;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import uk.co.jassoft.markets.datamodel.story.NamedEntities;
import uk.co.jassoft.markets.datamodel.story.NamedEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 *
 * @author Jonny
 */
@Component
public class SentimentAnalyser {

    private static final Logger LOG = LoggerFactory.getLogger(SentimentAnalyser.class);

    @Value("${SENTIMENT_API_REST_URL}")
    private String sentimentUrlRestUrl;

    @Autowired
    private RestTemplate restTemplate;

    private final ObjectMapper mapper = new ObjectMapper();

    public SentimentAnalyser() {
    }

    public NamedEntities analyseStory(final NamedEntities entities) {

        ConcurrentHashMap<String, Integer> sentenceSentiments = new ConcurrentHashMap<>();

        Stream<NamedEntity> allNamedEntities = Stream.of(entities.getOrganisations(), entities.getPeople(), entities.getLocations(), entities.getMisc()).flatMap(Collection::stream);

        allNamedEntities
                .filter(isMatched())
                .map(NamedEntity::getSentiments).flatMap(sentiments -> sentiments.stream()).forEach(sentiment -> {

            sentiment.setSentiment(sentenceSentiments.computeIfAbsent(sentiment.getSentence(), k -> {

                try {

                    MultiValueMap<String, String> map = new LinkedMultiValueMap<String, String>();
                    map.add("text", sentiment.getSentence());

                    String response = restTemplate.postForObject(sentimentUrlRestUrl, map, String.class);

                    JsonNode root = mapper.readTree(response);

                    JsonNode score = root.get("score");

                    return mapper.convertValue(score, Integer.class);
                }
                catch (Exception exception) {
                    LOG.error("Failed to determine sentiment for sentence.", exception);
                }

                return 0;
            }));
        });
        
        return entities;
    }

    public static Predicate<NamedEntity> isMatched() {
        return namedEntity -> namedEntity.isMatched();
    }
}
