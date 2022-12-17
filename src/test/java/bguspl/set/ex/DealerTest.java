package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import bguspl.set.UserInterface;
import bguspl.set.Util;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DealerTest {

    Dealer dealer;
    @Mock
    Player[] players;
    @Mock
    private UserInterface ui;
    @Mock
    private Table table;
    @Mock
    private Logger logger;
    @Mock
    private Util util;
    @Mock
    private Env env;

    private void assertInvariants() {
        assertTrue(dealer.getSetRequests().size() <= dealer.getPlayers().length);
    }

    @BeforeEach
    void setUp() {
        Env env = new Env(logger, new Config(logger, ""), ui, util);
        dealer = new Dealer(env, table, players);
        assertInvariants();
    }

    @AfterEach
    void tearDown() {assertInvariants();}

    @Test
    void StopWhenReachingFinishTerms() {
    }

    @Test
    void playerAddedToSetRequests() {

    }

}
