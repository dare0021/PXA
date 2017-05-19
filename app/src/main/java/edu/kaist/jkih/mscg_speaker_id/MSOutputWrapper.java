package edu.kaist.jkih.mscg_speaker_id;

import java.util.UUID;

/**
 * Data container
 *
 * Created by jkih on 2017-05-04.
 */

public class MSOutputWrapper
{
    public enum Result
    {
        Good, Bad
    }

    public int receipt;
    public Result result;
    public UUID id;
    public String alias;
    public String confidence;
}
