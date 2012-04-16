package testWordCount;

import java.io.*;
import java.util.*;
import java.io.IOException;
import java.util.Map.*;

import org.apache.hadoop.fs.Path;
import org.apache.commons.logging.*;
import org.apache.hadoop.conf.*;
import org.apache.hadoop.io.*;
import org.apache.hadoop.mapreduce.*;
import org.apache.hadoop.mapreduce.lib.input.*;
import org.apache.hadoop.util.*;
import org.bson.*;

import com.mongodb.hadoop.*;
import com.mongodb.hadoop.util.*;

public class WordCount extends Configured implements Tool {
    private static final Log log = LogFactory.getLog( WordCount.class );

    public int run( String[] args ) throws Exception{
        /**
         * ToolRunner will configure/process/setup the config
         * so we need to grab the classlevel one
         * This will be inited with any loaded xml files or -D prop=value params
         */
        final Configuration conf = getConf();

        log.info( "Created a conf: '" + conf + "' on {" + this.getClass() + "} as job named '" + _jobName + "'" );

        for ( final Entry<String, String> entry : conf ){
            log.trace( String.format( "%s=%s\n", entry.getKey(), entry.getValue() ) );
        }

        final Job job = new Job( conf, _jobName );
        /**
         * Any arguments specified with -D <property>=<value>
         * on the CLI will be picked up and set here
         * They override any XML level values
         * Note that -D<space> is important - no space will
         * not work as it get spicked up by Java itself
         */
        // TODO - Do we need to set job name somehow more specifically?
        // This may or may not be correct/sane
        job.setJarByClass( this.getClass() );
        final Class mapper = MongoConfigUtil.getMapper( conf );

        log.info( "Mapper Class: " + mapper );
        job.setMapperClass( mapper );
        Class<? extends Reducer> combiner = MongoConfigUtil.getCombiner(conf);
        if (combiner != null) {
            job.setCombinerClass( combiner );
        }
        job.setReducerClass( MongoConfigUtil.getReducer( conf ) );

        job.setOutputFormatClass( MongoConfigUtil.getOutputFormat( conf ) );
        job.setOutputKeyClass( MongoConfigUtil.getOutputKey( conf ) );
        job.setOutputValueClass( MongoConfigUtil.getOutputValue( conf ) );

        job.setInputFormatClass( TextInputFormat.class );
        FileInputFormat.setInputPaths(job, new Path("/mnt/hive_07_1/warehouse/test_input_file"));

        /**
         * Determines if the job will run verbosely e.g. print debug output
         * Only works with foreground jobs
         */
        final boolean verbose = MongoConfigUtil.isJobVerbose( conf );
        /**
         * Run job in foreground aka wait for completion or background?
         */
        final boolean background = MongoConfigUtil.isJobBackground( conf );
        try {
            if ( background ){
                log.info( "Setting up and running MapReduce job in background." );
                job.submit();
                return 0;
            }
            else{
                log.info( "Setting up and running MapReduce job in foreground, will wait for results.  {Verbose? "
                          + verbose + "}" );
                return job.waitForCompletion( true ) ? 0 : 1;
            }
        }
        catch ( final Exception e ) {
            log.error( "Exception while executing job... ", e );
            return 1;
        }
    }

    /**
     * Main will be a necessary method to run the job - suggested implementation
     * template:
     * public static void main(String[] args) throws Exception {
     * int exitCode = ToolRunner.run(new <YourClass>(), args);
     * System.exit(exitCode);
     * }
     *
     */

    /**
     * SET ME Defines the name of the job on the cluster. Left non-final to allow tweaking with serial #s, etc
     */
    String _jobName = "<unnamed MongoTool job>";

    public static class TokenizerMapper extends Mapper<LongWritable, Text, Text, IntWritable> {
        private final static IntWritable one = new IntWritable( 1 );
        private final Text word = new Text();

        public void map( LongWritable key, Text value, Context context ) throws IOException, InterruptedException{
            String line = value.toString();
            StringTokenizer tokenizer = new StringTokenizer(line);

            while ( tokenizer.hasMoreTokens() ){
                word.set( tokenizer.nextToken() );
                context.write( word, one );
            }
        }
    }

    public static class IntSumReducer extends Reducer<Text, IntWritable, Text, IntWritable> {

        private final IntWritable result = new IntWritable();

        public void reduce( Text key, Iterable<IntWritable> values, Context context )
                throws IOException, InterruptedException{

            int sum = 0;
            for ( final IntWritable val : values ){
                sum += val.get();
            }
            result.set( sum );
            context.write( key, result );
        }
    }

    static{
        // Load the XML config defined in hadoop-local.xml
        Configuration.addDefaultResource( "src/examples/hadoop-local.xml" );
        Configuration.addDefaultResource( "src/examples/mongo-defaults.xml" );
    }

    public static void main( String[] args ) throws Exception{
        final int exitCode = ToolRunner.run( new WordCount(), args );
        System.exit( exitCode );
    }
}
