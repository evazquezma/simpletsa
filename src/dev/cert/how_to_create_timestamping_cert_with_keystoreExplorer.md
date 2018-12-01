## Step 0 (optional): create a self signed CA certificate

- Create a new KeyStore PKCS#12

- Generate Key Pair
Extensions: 
  key usage: Certificate Signing
  Basic Constratins: Subject is a CA


## Step 1: create your TSA certificate and private key

- Open CA in KeystoreExplorer

- Right click and select Sign > Sign new key pair

- Configure TSA certificate
  Extended Key Usage: Time Stamping (mark as critical)

- Export key pair as PKCS#12

- Discard changes in CA keystore