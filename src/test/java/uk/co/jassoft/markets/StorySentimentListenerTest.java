package uk.co.jassoft.markets;

import org.springframework.beans.factory.annotation.Value;
import uk.co.jassoft.markets.datamodel.company.Company;
import uk.co.jassoft.markets.datamodel.company.CompanyBuilder;
import uk.co.jassoft.markets.datamodel.company.sentiment.StorySentiment;
import uk.co.jassoft.markets.datamodel.sources.SourceBuilder;
import uk.co.jassoft.markets.datamodel.story.*;
import uk.co.jassoft.markets.datamodel.system.Topic;
import uk.co.jassoft.markets.repository.CompanyRepository;
import uk.co.jassoft.markets.repository.SourceRepository;
import uk.co.jassoft.markets.repository.StoryRepository;
import uk.co.jassoft.markets.repository.StorySentimentRepository;
import uk.co.jassoft.markets.sentiment.SpringConfiguration;
import uk.co.jassoft.markets.sentiment.StorySentimentListener;
import uk.co.jassoft.markets.sentiment.lingual.SentimentAnalyser;
import uk.co.jassoft.utils.BaseRepositoryTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import java.net.URL;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

/**
 * Created by jonshaw on 18/03/2016.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = SpringConfiguration.class)
@IntegrationTest(value = "SENTIMENT_API_REST_URL=http://sentiment-api:8888")
public class StorySentimentListenerTest extends BaseRepositoryTest {

    @Autowired
    private StoryRepository storyRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private SourceRepository sourceRepository;
    @Autowired
    private StorySentimentRepository storySentimentRepository;

    @Autowired
    private RestOperations restOperations;

    @Autowired
    @InjectMocks
    private SentimentAnalyser sentimentAnalyser;

    @Autowired
    @InjectMocks
    private StorySentimentListener target;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private JmsTemplate jmsTemplate;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        super.setUp();

        storyRepository.deleteAll();
        companyRepository.deleteAll();
        sourceRepository.deleteAll();
        storySentimentRepository.deleteAll();
    }

    @Test
    public void onMessage_storyMatchesSourceExclusion_isDeleted() throws Exception {

        String sourceId = sourceRepository.save(SourceBuilder.aSource()
                .withExclusionList(Arrays.asList("test.com"))
                .build())
                .getId();

        String storyId = storyRepository.save(new StoryBuilder()
                .setParentSource(sourceId)
                .setUrl(new URL("http://test.com"))
                .createStory())
                .getId();

        target.sentimentAnalyse(storyId);

        assertEquals(0, storyRepository.count());
    }

    @Test
    public void onMessage_storyWithNoEntities_isDeleted() throws Exception {

        String sourceId = sourceRepository.save(SourceBuilder.aSource()
                .withExclusionList(Arrays.asList())
                .build())
                .getId();

        String storyId = storyRepository.save(new StoryBuilder()
                .setParentSource(sourceId)
                .setUrl(new URL("http://test.com"))
                .createStory())
                .getId();

        target.sentimentAnalyse(storyId);

        assertEquals(0, storyRepository.count());
    }

    @Test
    public void onMessage_storyWithEntities_generatesSentiment() throws Exception {

        String sourceId = sourceRepository.save(SourceBuilder.aSource()
                .withExclusionList(Arrays.asList())
                .build())
                .getId();

        String companyId = companyRepository.save(CompanyBuilder.aCompany()
                .withEntities(NamedEntitiesBuilder.aNamedEntities()
                        .withOrganisation(NamedEntityBuilder.aNamedEntity()
                                .withName("TestOrganisation")
                                .withCount(5)
                                .build())
                        .withOrganisation(NamedEntityBuilder.aNamedEntity()
                                .withName("Test Company")
                                .withCount(5)
                                .build())
                        .build())
                .build())
                .getId();

        String storyId = storyRepository.save(new StoryBuilder()
                .setParentSource(sourceId)
                .setUrl(new URL("http://test.com"))
                .setEntities(NamedEntitiesBuilder.aNamedEntities()
                        .withOrganisation(NamedEntityBuilder.aNamedEntity()
                                .withName("TestOrganisation")
                                .withCount(2)
                                .withMatched(true)
                                .withSentiments(Arrays.asList(
                                        SentimentBuilder.aSentiment()
                                                .withSentence("This is a really good sentiment for testing sentiment")
                                                .build()
                                ))
                                .build())
                        .build())
                .setMatchedCompanies(Arrays.asList(companyId))
                .createStory())
                .getId();

        when(restTemplate.postForObject(eq("http://sentiment-api:8888"), any(MultiValueMap.class), eq(String.class)))
                .thenReturn("{\"score\":3,\"comparative\":0.3333333333333333,\"tokens\":[\"this\",\"is\",\"a\",\"really\",\"good\",\"sentiment\",\"for\",\"testing\",\"sentiment\"],\"words\":[\"good\"],\"positive\":[\"good\"],\"negative\":[]}");

        target.sentimentAnalyse(storyId);

        assertEquals(1, storyRepository.count());

        final Story resultStory = storyRepository.findOne(storyId);

        assertNotNull(resultStory.getEntities());

        assertEquals(1, companyRepository.count());

        final Company resultCompany = companyRepository.findOne(companyId);

        List<StorySentiment> storySentimentList = storySentimentRepository.findAll();

        assertNotNull(storySentimentList);
        assertEquals(storyId, storySentimentList.get(0).getStory());
        assertEquals("TestOrganisation", storySentimentList.get(0).getEntitySentiment().get(0).getEntity());
        assertEquals(new Integer(15), storySentimentList.get(0).getEntitySentiment().get(0).getSentiment());

        Mockito.verify(jmsTemplate, Mockito.times(1)).convertAndSend(Mockito.eq(Topic.SentimentUpdated.toString()), Mockito.eq(companyId));
    }

    @Test
    public void onMessage_storyWithUnmatchedEntities_generatesSentiment() throws Exception {

        String sourceId = sourceRepository.save(SourceBuilder.aSource()
                .withExclusionList(Arrays.asList())
                .build())
                .getId();

        String companyId = companyRepository.save(CompanyBuilder.aCompany()
                .withEntities(NamedEntitiesBuilder.aNamedEntities()
                        .withOrganisation(NamedEntityBuilder.aNamedEntity()
                                .withName("TestOrganisation")
                                .withCount(5)
                                .build())
                        .build())
                .build())
                .getId();

        String storyId = storyRepository.save(new StoryBuilder()
                .setParentSource(sourceId)
                .setUrl(new URL("http://test.com"))
                .setEntities(NamedEntitiesBuilder.aNamedEntities()
                        .withOrganisation(NamedEntityBuilder.aNamedEntity()
                                .withName("TestOrganisation")
                                .withCount(2)
                                .withSentiments(Arrays.asList(SentimentBuilder.aSentiment()
                                        .withSentence("This is a really good sentiment for testing sentiment")
                                        .build()))
                                .build())
                        .build())
                .setMatchedCompanies(Arrays.asList(companyId))
                .createStory())
                .getId();

        target.sentimentAnalyse(storyId);

        assertEquals(1, storyRepository.count());

        final Story resultStory = storyRepository.findOne(storyId);

        assertNotNull(resultStory.getEntities());

        assertEquals(1, companyRepository.count());

        final Company resultCompany = companyRepository.findOne(companyId);

        List<StorySentiment> storySentimentList = storySentimentRepository.findAll();

        assertNotNull(storySentimentList);
        assertEquals(storyId, storySentimentList.get(0).getStory());
        assertEquals("TestOrganisation", storySentimentList.get(0).getEntitySentiment().get(0).getEntity());
        assertEquals(new Integer(0), storySentimentList.get(0).getEntitySentiment().get(0).getSentiment());
    }
}