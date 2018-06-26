# funduszeEuropejskieCrawler
Crawler example for https://www.funduszeeuropejskie.gov.pl/

1. Problem:
    PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target

Solution:
    On Windows the easiest way is to use the program portecle.

    Download and install portecle.
    First make 100% sure you know which JRE or JDK is being used to run your program. On a 64 bit Windows 7 there could be quite a few JREs. Process Explorer can help you with this or you can use: System.out.println(System.getProperty("java.home"));
    Copy the file JAVA_HOME\lib\security\cacerts to another folder.
    In Portecle click File > Open Keystore File
    Select the cacerts file
    Enter this password: changeit
    Click Tools > Import Trusted Certificate
    Browse for the file mycertificate.pem
    Click Import
    Click OK for the warning about the trust path.
    Click OK when it displays the details about the certificate.
    Click Yes to accept the certificate as trusted.
    When it asks for an alias click OK and click OK again when it says it has imported the certificate.
    Click save. Don’t forget this or the change is discarded.
    Copy the file cacerts back where you found it.

2. Files are compressed using DEFLATE64 -> Apache commons-compress 1.16 support it.

3. Jsoup need to have 1.11.3 version because of used methods.

4. While running tests, use:
-ea -Djazz.connector.sslProtocol=TLSv1 -Dcom.ibm.team.repository.transport.client.protocol=TLSv1 -Djdk.tls.client.protocols="TLSv1" -Dhttps.protocols="TLSv1"
