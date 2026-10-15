# Deployment vendor register

`data/ops/vendor_register.example.csv` is a synthetic template, not an inventory of KOKUTBON suppliers, contracts, accounts or compliance status.

Deployment owners maintain the actual register in a private system. Assign owners, assess risk and data sensitivity, schedule reviews, and verify fallback and restore procedures. Do not commit contracts, account identifiers, contacts, credentials or operational evidence.

`bash scripts/ci/vendor_register_check.sh` validates the example schema only; passing does not attest to deployment compliance or vendor approval.
