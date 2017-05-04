package edu.kaist.jkih.mscg_speaker_id;

import com.microsoft.cognitive.speakerrecognition.contract.Confidence;

import java.util.UUID;

/**
 * Data container
 *
 * Created by jkih on 2017-05-04.
 */

public class MSOutputWrapper
{
    public int receipt;
    public UUID id;
    public String alias;
    public String confidence;
}
