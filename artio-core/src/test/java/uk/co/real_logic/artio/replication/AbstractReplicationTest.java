/*
 * Copyright 2015-2017 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.real_logic.artio.replication;

import io.aeron.Aeron;
import io.aeron.ExclusivePublication;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import org.agrona.DirectBuffer;
import org.agrona.IoUtil;
import org.agrona.concurrent.NoOpIdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.AtomicCounter;
import org.junit.After;
import org.junit.Before;
import uk.co.real_logic.artio.TestFixtures;
import uk.co.real_logic.artio.engine.CompletionPosition;
import uk.co.real_logic.artio.engine.logger.ArchiveMetaData;
import uk.co.real_logic.artio.engine.logger.Archiver;
import uk.co.real_logic.artio.engine.logger.LogDirectoryDescriptor;

import java.io.File;

import static org.agrona.CloseHelper.close;
import static org.mockito.Mockito.mock;
import static uk.co.real_logic.artio.CommonConfiguration.DEFAULT_NAME_PREFIX;
import static uk.co.real_logic.artio.TestFixtures.cleanupMediaDriver;
import static uk.co.real_logic.artio.Timing.assertEventuallyTrue;
import static uk.co.real_logic.artio.engine.EngineConfiguration.DEFAULT_LOGGER_CACHE_NUM_SETS;
import static uk.co.real_logic.artio.engine.EngineConfiguration.DEFAULT_LOGGER_CACHE_SET_SIZE;

public class AbstractReplicationTest
{
    protected static final String IPC = "aeron:ipc";
    protected static final int FRAGMENT_LIMIT = 1;
    protected static final long TIMEOUT = 100;
    protected static final long HEARTBEAT_INTERVAL = TIMEOUT / 2;
    protected static final int CLUSTER_SIZE = 3;
    protected static final long TIME = 0L;
    protected static final int DATA_SESSION_ID = 43;
    protected static final DirectBuffer NODE_STATE_BUFFER = new UnsafeBuffer(new byte[1]);

    protected NodeStateHandler nodeStateHandler = mock(NodeStateHandler.class);

    protected ClusterAgent clusterNode1 = mock(ClusterAgent.class);
    protected ClusterAgent clusterNode2 = mock(ClusterAgent.class);
    protected ClusterAgent clusterNode3 = mock(ClusterAgent.class);

    protected TermState termState1 = new TermState();
    protected TermState termState2 = new TermState();
    protected TermState termState3 = new TermState();

    protected MediaDriver mediaDriver;
    protected Aeron aeron;

    protected Subscription controlSubscription()
    {
        return aeron.addSubscription(IPC, ClusterConfiguration.DEFAULT_CONTROL_STREAM_ID);
    }

    protected Subscription acknowledgementSubscription()
    {
        return aeron.addSubscription(IPC, ClusterConfiguration.DEFAULT_ACKNOWLEDGEMENT_STREAM_ID);
    }

    protected Subscription dataSubscription()
    {
        return aeron.addSubscription(IPC, ClusterConfiguration.DEFAULT_DATA_STREAM_ID);
    }

    protected RaftPublication raftPublication(final int streamId)
    {
        return new RaftPublication(
            100,
            new NoOpIdleStrategy(),
            mock(AtomicCounter.class),
            aeron.addExclusivePublication(IPC, streamId));
    }

    protected ExclusivePublication dataPublication()
    {
        return aeron.addExclusivePublication(IPC, ClusterConfiguration.DEFAULT_DATA_STREAM_ID);
    }

    @Before
    public void setupAeron()
    {
        mediaDriver = TestFixtures.launchMediaDriver();
        aeron = Aeron.connect();
    }

    @After
    public void teardownAeron()
    {
        deleteLogDir(1);
        deleteLogDir(2);
        deleteLogDir(3);

        close(aeron);
        cleanupMediaDriver(mediaDriver);
    }

    private void deleteLogDir(final int id)
    {
        IoUtil.delete(new File(logFileDir((short)id)), true);
    }

    protected static int poll(final Role role)
    {
        return role.poll(FRAGMENT_LIMIT, 0);
    }

    protected static void poll1(final Role role)
    {
        assertEventuallyTrue(
            "Unable to poll 1 message",
            () -> role.poll(FRAGMENT_LIMIT, 0) > 0);
    }

    protected Follower follower(
        final short id,
        final ClusterAgent clusterNode,
        final TermState termState)
    {
        final ArchiveMetaData metaData = archiveMetaData(id);
        final Subscription subscription = dataSubscription();
        final StreamIdentifier streamId = new StreamIdentifier(subscription);
        final Archiver archiver = new Archiver(
            metaData,
            DEFAULT_LOGGER_CACHE_NUM_SETS,
            DEFAULT_LOGGER_CACHE_SET_SIZE,
            streamId,
            DEFAULT_NAME_PREFIX,
            mock(CompletionPosition.class))
            .subscription(subscription);

        return new Follower(
            id,
            clusterNode,
            0,
            TIMEOUT,
            termState,
            new RaftArchiver(termState.leaderSessionId(), archiver),
            NODE_STATE_BUFFER,
            nodeStateHandler)
            .controlSubscription(controlSubscription())
            .acknowledgementPublication(raftPublication(ClusterConfiguration.DEFAULT_ACKNOWLEDGEMENT_STREAM_ID))
            .controlPublication(raftPublication(ClusterConfiguration.DEFAULT_CONTROL_STREAM_ID))
            .follow(0);
    }

    public static ArchiveMetaData archiveMetaData(final short nodeId)
    {
        final LogDirectoryDescriptor descriptor = new LogDirectoryDescriptor(logFileDir(nodeId));
        return new ArchiveMetaData(descriptor);
    }

    static String logFileDir(final short id)
    {
        return IoUtil.tmpDirName() + "/node" + id;
    }
}
