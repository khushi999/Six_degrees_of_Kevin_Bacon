*** Settings ***
Library           Collections
Library           RequestsLibrary
Test Timeout      30 seconds

Suite Setup       Create Session    localhost    http://localhost:8080

*** Test Cases ***
addActorPass
    ${headers}=     Create Dictionary    Content-Type=application/json
    ${params}=      Create Dictionary    name=Johnny Depp    actorId=jd1
    ${resp}=        PUT On Session       localhost    /api/v1/addActor    json=${params}    headers=${headers}    expected_status=200

addActorFail
    ${headers}=     Create Dictionary    Content-Type=application/json
    ${params}=      Create Dictionary    name=Angelina Jolie
    ${resp}=        PUT On Session       localhost    /api/v1/addActor    json=${params}    headers=${headers}    expected_status=400

addMoviePass
    ${headers}=     Create Dictionary    Content-Type=application/json
    ${params}=      Create Dictionary    name=Pirates of the Caribbean    movieId=pc1
    ${resp}=        PUT On Session       localhost    /api/v1/addMovie    json=${params}    headers=${headers}    expected_status=200

addMovieFail
    ${headers}=     Create Dictionary    Content-Type=application/json
    ${params}=      Create Dictionary    actor=Orlando Bloom    movieId=pc2
    ${resp}=        PUT On Session       localhost    /api/v1/addMovie    json=${params}    headers=${headers}    expected_status=400

addRelationshipPass
    ${headers}=     Create Dictionary    Content-Type=application/json
    ${params}=      Create Dictionary    actorId=jd1    movieId=pc1
    ${resp}=        PUT On Session       localhost    /api/v1/addRelationship    json=${params}    headers=${headers}    expected_status=200
    
addRelationshipFail
    ${headers}=     Create Dictionary    Content-Type=application/json
    ${params}=      Create Dictionary    actorId=aj1    movieId=pc1
    ${resp}=        PUT On Session       localhost    /api/v1/addRelationship    json=${params}    headers=${headers}    expected_status=404
    
addNationalityPass
    ${headers}=     Create Dictionary    Content-Type=application/json
    ${params}=      Create Dictionary    actorId=jd1    nationality=American
    ${resp}=        PUT On Session       localhost    /api/v1/addNationality    json=${params}    headers=${headers}    expected_status=200

addNationalityFail
    ${headers}=     Create Dictionary    Content-Type=application/json
    ${params}=      Create Dictionary    actorId=jd1    
    ${resp}=        PUT On Session       localhost    /api/v1/addMovie    json=${params}    headers=${headers}    expected_status=400
    
getActorPass
    ${headers}=     Create Dictionary    Content-Type=application/json
    ${params}=      Create Dictionary    actorId=jd1
    ${resp}=        GET On Session        localhost    /api/v1/getActor    params=${params}    headers=${headers}    expected_status=200
    Dictionary Should Contain Value    ${resp.json()}    Johnny Depp

getActorFail
    ${headers}=     Create Dictionary    Content-Type=application/json
    ${params}=      Create Dictionary    actorId=aj1
    ${resp}=        GET On Session        localhost    /api/v1/getActor    params=${params}    headers=${headers}    expected_status=404

getMoviePass
    ${headers}=     Create Dictionary    Content-Type=application/json
    ${params}=      Create Dictionary    movieId=pc1
    ${resp}=        GET On Session        localhost    /api/v1/getMovie    params=${params}    headers=${headers}    expected_status=200
    Dictionary Should Contain Value    ${resp.json()}    Pirates of the Caribbean

getMovieFail
    ${headers}=     Create Dictionary    Content-Type=application/json
    ${params}=      Create Dictionary    movieId=invalid
    ${resp}=        GET On Session        localhost    /api/v1/getMovie    params=${params}    headers=${headers}    expected_status=404

hasRelationshipPass
    ${headers}=     Create Dictionary    Content-Type=application/json
    ${params}=      Create Dictionary    actorId=jd1    movieId=pc1
    ${resp}=        GET On Session        localhost    /api/v1/hasRelationship    params=${params}    headers=${headers}    expected_status=200
    Dictionary Should Contain Value    ${resp.json()}    ${true}

hasRelationshipFail
    ${headers}=     Create Dictionary    Content-Type=application/json
    ${params}=      Create Dictionary    actorId=aj1    movieId=pc1
    ${resp}=        GET On Session        localhost    /api/v1/hasRelationship    params=${params}    headers=${headers}    expected_status=404
    
getNationalityPass
    ${headers}=     Create Dictionary    Content-Type=application/json
    ${params}=      Create Dictionary    nationality=American
    ${resp}=        GET On Session        localhost    /api/v1/getNationality    params=${params}    headers=${headers}    expected_status=200
    
    ${value}= 		GET From Dictionary   ${resp.json()}    actors
    Should Be Equal As Strings    ${value}    ['Johnny Depp']
    

getNationalityFail
    ${headers}=     Create Dictionary    Content-Type=application/json
    ${params}=      Create Dictionary    nationality=Indian
    ${resp}=        GET On Session        localhost    /api/v1/getNationality    params=${params}    headers=${headers}    expected_status=404
    

computeBaconNumberPass
    ${headers}=     Create Dictionary    Content-Type=application/json
    ${params}=      Create Dictionary    actorId=nm0000102
    ${resp}=        GET On Session        localhost    /api/v1/computeBaconNumber    params=${params}    headers=${headers}    expected_status=200
    Dictionary Should Contain Value    ${resp.json()}    ${0}

computeBaconNumberFail
    ${headers}=     Create Dictionary    Content-Type=application/json
    ${params}=      Create Dictionary    actorId=invalid
    ${resp}=        GET On Session        localhost    /api/v1/computeBaconNumber    params=${params}    headers=${headers}    expected_status=404
    
computeBaconPathPass
    ${headers}=     Create Dictionary    Content-Type=application/json
    ${params}=      Create Dictionary    actorId=jd1  
    ${resp}=        GET On Session        localhost    /api/v1/computeBaconPath    params=${params}    headers=${headers}    expected_status=200
    ${value}= 		GET From Dictionary   ${resp.json()}    baconPath
    Should Be Equal As Strings    ${value}    ['jd1', 'pc1', 'nm0000102']
 
computeBaconPathFail
    ${headers}=     Create Dictionary    Content-Type=application/json
    ${params}=      Create Dictionary    actorId=actorIdNotExisting
    ${resp}=        GET On Session        localhost    /api/v1/computeBaconPath    params=${params}    headers=${headers}    expected_status=404
    
    
    
    
    
    
    
    
    
