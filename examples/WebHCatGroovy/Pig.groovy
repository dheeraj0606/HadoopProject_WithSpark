/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import com.jayway.jsonpath.JsonPath
import groovy.json.JsonSlurper
import org.apache.hadoop.gateway.shell.Hadoop
import org.apache.hadoop.gateway.shell.hdfs.Hdfs
import org.apache.hadoop.gateway.shell.job.Job

import static java.util.concurrent.TimeUnit.SECONDS

def env = System.getenv()
gateway = env.gateway
username = env.username
password = env.password

jobDir = "/user/" + username + "/test"

session = Hadoop.login( gateway, username, password )

println "[Pig.groovy] Delete " + jobDir + ": " + Hdfs.rm( session ).file( jobDir ).recursive().now().statusCode
println "[Pig.groovy] Mkdir " + jobDir + ": " + Hdfs.mkdir( session ).dir( jobDir ).now().statusCode

id_pig = '''
A = load 'test/input/passwd' using PigStorage(':');
B = foreach A generate $0 as id;
dump B;
'''

fake_passwd = '''ctdean:Chris Dean:secret
pauls:Paul Stolorz:good
carmas:Carlos Armas:evil
dra:Deirdre McClure:marvelous
'''

println "[Pig.groovy] Copy Pig query file to HDFS"
Hdfs.put(session).text( id_pig ).to( jobDir + "/input/id.pig" ).now()
println "[Pig.groovy] Copy data file to HDFS"
Hdfs.put(session).text( fake_passwd ).to( jobDir + "/input/passwd" ).now()

jobId = Job.submitPig(session) \
            .file("${jobDir}/input/id.pig") \
            .arg("-v") \
            .statusDir("${jobDir}/output") \
            .now().jobId

println "[Pig.groovy] Submitted job: " + jobId

println "[Pig.groovy] Polling up to 60s for job completion..."
done = false
count = 0
while( !done && count++ < 120 ) {
  sleep( 1000 )
  json = Job.queryStatus(session).jobId(jobId).now().string
  done = JsonPath.read( json, "\$.status.jobComplete" )
  print "."; System.out.flush();
}
println ""
println "[Pig.groovy] Job status: " + done


text = Hdfs.ls( session ).dir( jobDir + "/output" ).now().string
json = (new JsonSlurper()).parseText( text )
println json.FileStatuses.FileStatus.pathSuffix

println "[Pig.groovy] Content of stdout:"
println Hdfs.get( session ).from( jobDir + "/output/stdout" ).now().string


session.shutdown()
