/*
 * ============LICENSE_START====================================================
 * org.onap.music.mdbc
 * =============================================================================
 * Copyright (C) 2019 AT&T Intellectual Property. All rights reserved.
 * =============================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ============LICENSE_END======================================================
 */

package org.onap.music.mdbc.ownership;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.lang3.tuple.Pair;
import org.onap.music.exceptions.MDBCServiceException;
import org.onap.music.logging.EELFLoggerDelegate;
import org.onap.music.mdbc.Range;
import org.onap.music.mdbc.mixins.DBInterface;
import org.onap.music.mdbc.mixins.LockResult;
import org.onap.music.mdbc.mixins.MusicInterface;
import org.onap.music.mdbc.tables.MriReference;
import org.onap.music.mdbc.tables.MusicRangeInformationRow;
import org.onap.music.mdbc.tables.MusicTxDigestId;
import org.onap.music.mdbc.tables.StagingTable;

public class OwnershipAndCheckpoint{

    private EELFLoggerDelegate logger = EELFLoggerDelegate.getLogger(OwnershipAndCheckpoint.class);
    private Lock checkpointLock;
    private AtomicBoolean change;
    private Map<Range, Pair<MriReference, Integer>> alreadyApplied;
    private Map<UUID,Long> ownershipBeginTime;
    private long timeoutInMs;

    public OwnershipAndCheckpoint(){
      this(new HashMap<>(),Long.MAX_VALUE);
    }

    public OwnershipAndCheckpoint(Map<Range, Pair<MriReference, Integer>> alreadyApplied, long timeoutInMs){
        change = new AtomicBoolean(true);
        checkpointLock = new ReentrantLock();
        this.alreadyApplied = alreadyApplied;
        ownershipBeginTime = new HashMap<>();
        this.timeoutInMs =  timeoutInMs;
    }

    public void startOwnershipTimeoutClock(UUID id){
        ownershipBeginTime.put(id,System.currentTimeMillis());
    }

    public void stopOwnershipTimeoutClock(UUID id){
        if(ownershipBeginTime.containsKey(id)) {
            ownershipBeginTime.remove(id);
        }
        else{
            logger.warn("clock was deleted with an invalid/stale id "+id);
        }
    }

    public boolean timeout(UUID id) throws MDBCServiceException {
        long current = System.currentTimeMillis();
        if(!ownershipBeginTime.containsKey(id)){
            throw new MDBCServiceException("timeout was call with an invalid id");
        }
        Long beginTime = ownershipBeginTime.get(id);
        if(current-beginTime > timeoutInMs){
            return true;
        }
        return false;
    }

    /**
     * Extracts all the rows that match any of the ranges.
     * @param allMriRows
     * @param ranges - ranges interested in
     * @param onlyIsLatest - only return the "latest" rows
     * @return
     */
    public List<MusicRangeInformationRow> extractRowsForRange(List<MusicRangeInformationRow> allMriRows, List<Range> ranges,
                                                  boolean onlyIsLatest){
        List<MusicRangeInformationRow> rows = new ArrayList<>();
        for(MusicRangeInformationRow row : allMriRows){
            if(onlyIsLatest && !row.getIsLatest()){
                continue;
            }
            final List<Range> rowRanges = row.getDBPartition().getSnapshot();
            boolean found = false;
            for(Range sRange : ranges){
                for(Range rRange: rowRanges) {
                    if(sRange.overlaps(rRange)){
                        rows.add(row);
                        found=true;
                        break;
                    }
                }
                if(found) break;
            }
        }
        return rows;
    }

    private List<MusicRangeInformationRow> extractRowsForRange(MusicInterface music, List<Range> ranges, boolean onlyIsLatest)
        throws MDBCServiceException {
        final List<MusicRangeInformationRow> allMriRows = music.getAllMriRows();
        return extractRowsForRange(allMriRows,ranges,onlyIsLatest);
    }

    /**
     * make sure data is up to date for list of ranges
     * @param mi
     * @param di
     * @param extendedDag
     * @param ranges
     * @param locks
     * @param ownOpId
     * @throws MDBCServiceException
     */
    public void checkpoint(MusicInterface mi, DBInterface di, Dag extendedDag, List<Range> ranges,
        Map<MusicRangeInformationRow, LockResult> locks, UUID ownOpId) throws MDBCServiceException {
        if(ranges.isEmpty()){
            return;
        }
        try {
            checkpointLock.lock();
            change.set(true);
            Set<Range> rangesSet = new HashSet<>(ranges);
            extendedDag.setAlreadyApplied(alreadyApplied, rangesSet);
            applyRequiredChanges(mi, di, extendedDag, ranges, ownOpId);
        }
        catch(MDBCServiceException e){
            stopOwnershipTimeoutClock(ownOpId);
            throw e;
        }
        finally {
            checkpointLock.unlock();
        }
    }

    private void enableForeignKeys(DBInterface di) throws MDBCServiceException {
        try {
            di.enableForeignKeyChecks();
        } catch (SQLException e) {
            throw new MDBCServiceException("Error enabling foreign keys checks",e);
        }
    }

    private void disableForeignKeys(DBInterface di) throws MDBCServiceException {
        try {
            di.disableForeignKeyChecks();
        } catch (SQLException e) {
            throw new MDBCServiceException("Error disable foreign keys checks",e);
        }
    }

    private void applyTxDigest(List<Range> ranges, DBInterface di, StagingTable txDigest)
        throws MDBCServiceException {
        try {
            di.applyTxDigest(txDigest,ranges);
        } catch (SQLException e) {
            throw new MDBCServiceException("Error applying tx digest in local SQL",e);
        }
    }

    public void warmup(MusicInterface mi, DBInterface di, List<Range> ranges) throws MDBCServiceException {
        if(ranges.isEmpty()){
            return;
        }
        boolean ready = false;
        change.set(true);
        Set<Range> rangeSet = new HashSet<Range>(ranges);
        Dag dag = new Dag(false);
        while(!ready){
            if(change.get()){
                change.set(false);
                final List<MusicRangeInformationRow> rows = extractRowsForRange(mi, ranges,false);
                dag = Dag.getDag(rows,ranges);
            }
            else if(!dag.applied()){
                DagNode node = dag.nextToApply(ranges);
                if(node!=null) {
                    Pair<MusicTxDigestId, List<Range>> pair = node.nextNotAppliedTransaction(rangeSet);
                    while (pair != null) {
                        disableForeignKeys(di);
                        checkpointLock.lock();
                        if (change.get()) {
                            enableForeignKeys(di);
                            checkpointLock.unlock();
                            break;
                        } else {
                            final StagingTable txDigest = mi.getTxDigest(pair.getKey());
                            applyTxDigest(ranges,di, txDigest);
                            for (Range r : pair.getValue()) {
                                MusicRangeInformationRow row = node.getRow();
                                alreadyApplied.put(r, Pair.of(new MriReference(row.getPartitionIndex()), pair.getKey().index));
                            }
                        }
                        pair = node.nextNotAppliedTransaction(rangeSet);
                        enableForeignKeys(di);
                        checkpointLock.unlock();
                    }
                }
            }
            else{
                ready = true;
            }
        }
    }

    private void applyRequiredChanges(MusicInterface mi, DBInterface db, Dag extendedDag, List<Range> ranges, UUID ownOpId)
        throws MDBCServiceException {
        Set<Range> rangeSet = new HashSet<Range>(ranges);
        disableForeignKeys(db);
        while(!extendedDag.applied()){
            DagNode node = extendedDag.nextToApply(ranges);
            if(node!=null) {
                Pair<MusicTxDigestId, List<Range>> pair = node.nextNotAppliedTransaction(rangeSet);
                while (pair != null) {
                    final StagingTable txDigest = mi.getTxDigest(pair.getKey());
                    applyTxDigest(ranges, db, txDigest);
                    for (Range r : pair.getValue()) {
                        MusicRangeInformationRow row = node.getRow();
                        alreadyApplied.put(r, Pair.of(new MriReference(row.getPartitionIndex()), pair.getKey().index));
                    }
                    pair = node.nextNotAppliedTransaction(rangeSet);
                    if (timeout(ownOpId)) {
                        enableForeignKeys(db);
                        throw new MDBCServiceException("Timeout apply changes to local dbi");
                    }
                }
            }
        }
        enableForeignKeys(db);

    }

}
