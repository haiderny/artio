/*
 * Copyright 2015 Real Logic Ltd.
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
package uk.co.real_logic.fix_gateway.session;

import uk.co.real_logic.fix_gateway.builder.HeaderEncoder;
import uk.co.real_logic.fix_gateway.decoder.HeaderDecoder;

import java.util.Arrays;

/**
 * A simple, and dumb session id Strategy based upon hashing SenderCompID and TargetCompID. Makes no assumptions
 * about the nature of either identifiers.
 *
 * Threadsafe - no state;
 */
public class SenderTargetAndSubSessionIdStrategy implements SessionIdStrategy
{
    public Object onAcceptorLogon(final HeaderDecoder header)
    {
        final char[] senderCompID = Arrays.copyOf(header.targetCompID(), header.targetCompIDLength());
        final char[] senderSubID = Arrays.copyOf(header.targetSubID(), header.targetSubIDLength());
        final char[] targetCompId = Arrays.copyOf(header.senderCompID(), header.senderCompIDLength());
        return new CompositeKey(senderCompID, senderSubID, targetCompId);
    }

    public Object onInitiatorLogon(
        final String senderCompId, final String senderSubId, final String senderLocationId, final String targetCompId)
    {
        return new CompositeKey(senderCompId.toCharArray(), senderSubId.toCharArray(), targetCompId.toCharArray());
    }

    public void setupSession(final Object compositeKey, final HeaderEncoder encoder)
    {
        final CompositeKey composite = (CompositeKey) compositeKey;
        encoder.senderCompID(composite.senderCompID);
        encoder.senderSubID(composite.senderSubID);
        encoder.targetCompID(composite.targetCompID);
    }

    private static final class CompositeKey
    {
        private final char[] senderCompID;
        private final char[] senderSubID;
        private final char[] targetCompID;
        private final int hashCode;

        private CompositeKey(final char[] senderCompID, final char[] senderSubID, final char[] targetCompID)
        {
            this.senderCompID = senderCompID;
            this.senderSubID = senderSubID;
            this.targetCompID = targetCompID;

            hashCode = hash(senderCompID, senderSubID, targetCompID);
        }

        private int hash(final char[] senderCompID, final char[] senderSubID, final char[] targetCompID)
        {
            int result  = Arrays.hashCode(senderCompID);
            result = 31 * result + Arrays.hashCode(senderSubID);
            result = 31 * result + Arrays.hashCode(targetCompID);
            return result;
        }

        public int hashCode()
        {
            return hashCode;
        }

        public boolean equals(final Object obj)
        {
            if (obj instanceof CompositeKey)
            {
                final CompositeKey compositeKey = (CompositeKey)obj;
                return Arrays.equals(compositeKey.senderCompID, senderCompID)
                    && Arrays.equals(compositeKey.senderSubID, senderSubID)
                    && Arrays.equals(compositeKey.targetCompID, targetCompID);
            }

            return false;
        }

        public String toString()
        {
            return "CompositeKey{" +
                "senderCompID=" + Arrays.toString(senderCompID) +
                "senderSubID=" + Arrays.toString(senderSubID) +
                ", targetCompID=" + Arrays.toString(targetCompID) +
                '}';
        }
    }
}