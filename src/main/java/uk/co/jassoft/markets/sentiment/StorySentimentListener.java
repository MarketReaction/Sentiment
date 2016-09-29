/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package uk.co.jassoft.markets.sentiment;

import uk.co.jassoft.markets.datamodel.company.sentiment.EntitySentiment;
import uk.co.jassoft.markets.datamodel.company.sentiment.StorySentiment;
import uk.co.jassoft.markets.datamodel.sources.Source;
import uk.co.jassoft.markets.datamodel.story.NamedEntities;
import uk.co.jassoft.markets.datamodel.story.NamedEntity;
import uk.co.jassoft.markets.datamodel.story.Sentiment;
import uk.co.jassoft.markets.datamodel.story.Story;
import uk.co.jassoft.markets.datamodel.story.metric.Metric;
import uk.co.jassoft.markets.datamodel.story.metric.MetricBuilder;
import uk.co.jassoft.markets.datamodel.system.Topic;
import uk.co.jassoft.markets.repository.CompanyRepository;
import uk.co.jassoft.markets.repository.SourceRepository;
import uk.co.jassoft.markets.repository.StoryRepository;
import uk.co.jassoft.markets.repository.StorySentimentRepository;
import uk.co.jassoft.markets.sentiment.lingual.SentimentAnalyser;
import uk.co.jassoft.markets.utils.SourceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 *
 * @author Jonny
 */
@Component
public class StorySentimentListener
{
    private static final Logger LOG = LoggerFactory.getLogger(StorySentimentListener.class);

    @Autowired
    protected MongoTemplate mongoTemplate; // TODO: replace with function on Repository
    @Autowired
    private StoryRepository storyRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private SourceRepository sourceRepository;
    @Autowired
    private StorySentimentRepository storySentimentRepository;
    @Autowired
    private SentimentAnalyser sentimentAnalyser;

    @Autowired
    private JmsTemplate jmsTemplate;

    private void sentimentUpdated(final String message)
    {
        jmsTemplate.convertAndSend(Topic.SentimentUpdated.toString(), message);
    }

    public void sentimentAnalyse(final String storyId)
    {
        final Date start = new Date();

        try
        {
            Story story = storyRepository.findOne(storyId);

            if(story == null)
                return;

            Source source = sourceRepository.findOne(story.getParentSource());

            if(SourceUtils.matchesExclusion(source.getExclusionList(), story.getUrl().toString()))
            {
                LOG.warn("Duplicate Story or URL in exclusion List [{}] Stopping processing at [{}]", story.getUrl().toString(), this.getClass().getName());
                storyRepository.delete(story.getId());
                return;
            }

            LOG.info("Running Sentiment For Story [{}]", story.getUrl());

            if(story.getEntities() == null)
            {
                LOG.warn("Story [{}] Has no entities Stopping processing at [{}]", story.getUrl().toString(), this.getClass().getName());
                storyRepository.delete(story.getId());
                return;
            }

            NamedEntities entities = sentimentAnalyser.analyseStory(story.getEntities());

            Set<String> updatedCompanyIds = new HashSet<>();

            story.getMatchedCompanies().stream()
                    .map(companyId -> companyRepository.findOne(companyId))
                    .forEach(company -> {

                        Stream.of(entities.getOrganisations(), entities.getPeople(), entities.getLocations(), entities.getMisc()).flatMap(Collection::stream)
                                .filter(namedEntity -> Stream.of(company.getEntities().getOrganisations(), company.getEntities().getPeople(), company.getEntities().getLocations(), company.getEntities().getMisc()).flatMap(Collection::stream)
                                        .collect(Collectors.toList()).contains(namedEntity))
                                .forEach(namedEntity -> {
                                    // Where a story Named Entity is the same as a Company Named Entity
                                    final StorySentiment storySentiment = new StorySentiment(company.getId(), story.getDatePublished(), story.getId());

                                    int multiplier = Stream.of(company.getEntities().getOrganisations(), company.getEntities().getPeople(), company.getEntities().getLocations(), company.getEntities().getMisc()).flatMap(Collection::stream)
                                            .filter(companyNamedEntity -> companyNamedEntity.equals(namedEntity)).collect(Collectors.summingInt(NamedEntity::getCount));

                                    storySentiment.getEntitySentiment().add(new EntitySentiment(namedEntity.getName(), namedEntity.getSentiments().stream().collect(Collectors.summingInt(Sentiment::getSentiment)) * multiplier));

                                    storySentimentRepository.save(storySentiment);

                                    updatedCompanyIds.add(company.getId());
                                });
                    });

            Metric metric = MetricBuilder.aSentimentMetric().withStart(start).withEndNow().build();
            mongoTemplate.updateFirst(Query.query(Criteria.where("id").is(story.getId())), new Update().set("entities", entities).push("metrics", metric), Story.class);

            updatedCompanyIds.parallelStream().forEach(companyId -> {
                LOG.info("Because of Story [{}], Updated sentiment of company [{}]", story.getId(), companyId);
                sentimentUpdated(companyId);
            });
        }
        catch (final RuntimeException exception)
        {
            LOG.error(exception.getLocalizedMessage(), exception);

            throw new RuntimeException(exception);
        }
        catch (final Exception exception)
        {
            LOG.error(exception.getLocalizedMessage(), exception);

            throw new RuntimeException(exception);
        }
    }

}
