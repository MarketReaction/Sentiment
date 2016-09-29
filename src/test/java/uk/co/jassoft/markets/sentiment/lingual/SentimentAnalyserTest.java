package uk.co.jassoft.markets.sentiment.lingual;

import uk.co.jassoft.markets.datamodel.exclusion.Exclusion;
import uk.co.jassoft.markets.datamodel.story.NamedEntities;
import uk.co.jassoft.markets.datamodel.story.NamedEntity;
import uk.co.jassoft.markets.datamodel.story.Sentiment;
import uk.co.jassoft.markets.repository.ExclusionRepository;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by jonshaw on 19/08/15.
 */
@Ignore("Depends on external service")
public class SentimentAnalyserTest {

    private SentimentAnalyser sentimentAnalyser;

    @Before
    public void setUp() {
        ExclusionRepository exclusionRepository = Mockito.mock(ExclusionRepository.class);

        Mockito.when(exclusionRepository.findByName(Mockito.anyString())).thenReturn(new ArrayList<Exclusion>());

        sentimentAnalyser = new SentimentAnalyser();
    }

    @Test
    public void testAnalyseStory() throws Exception {

        NamedEntities sourceNamedEntities = new NamedEntities();
        List<Sentiment> sentiments = new ArrayList<>();
        sentiments.add(new Sentiment("Apple is really good", 0));
        sourceNamedEntities.getOrganisations().add(new NamedEntity("Apple", 5, true, sentiments));

        NamedEntities namedEntities = sentimentAnalyser.analyseStory(sourceNamedEntities);

        for(NamedEntity namedEntity : namedEntities.getOrganisations())
        {
            for(Sentiment sentiment : namedEntity.getSentiments())
            {
                assertEquals("Apple is really good", sentiment.getSentence());
                assertNotEquals(0, sentiment.getSentiment());
                assertTrue(sentiment.getSentiment() > 0);
            }
        }
    }

    @Test
    public void testAnalyseStory_withMultipleSentences() throws Exception {

        NamedEntities sourceNamedEntities = new NamedEntities();
        List<Sentiment> sentiments = new ArrayList<>();
        sentiments.add(new Sentiment("Apple is really good", 0));
        sourceNamedEntities.getOrganisations().add(new NamedEntity("Apple", 5, true, sentiments));

        List<Sentiment> sentiments2 = new ArrayList<>();
        sentiments2.add(new Sentiment("But Samsung is really bad", 0));
        sourceNamedEntities.getOrganisations().add(new NamedEntity("Samsung", 3, true, sentiments2));

        NamedEntities namedEntities = sentimentAnalyser.analyseStory(sourceNamedEntities);

        assertEquals(2, namedEntities.getOrganisations().size());

        for(NamedEntity namedEntity : namedEntities.getOrganisations())
        {
            for(Sentiment sentiment : namedEntity.getSentiments())
            {
                assertNotEquals(0, sentiment.getSentiment());
            }
        }
    }
}