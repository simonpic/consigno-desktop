module consigno.crypto {
    requires consigno.common;

    requires org.bouncycastle.provider;
    requires org.bouncycastle.pkix;
    requires org.slf4j;

    exports com.consigno.crypto.service;
}
