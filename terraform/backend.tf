# Remote state in S3 with DynamoDB locking. Uses partial configuration so the
# bucket/table/key names are supplied per environment via backend "s3" configs
# (see README bootstrap section) — never commit real bucket names with state.
terraform {
  backend "s3" {
    # bootstrap: fill key/region here or pass -backend-config=...
  }
}
