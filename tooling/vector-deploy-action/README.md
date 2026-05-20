# vector-deploy-action

Composite GitHub Action that signs and POSTs a Vector deploy webhook.

## Usage

```yaml
- uses: filipnikolov1/vector-deploy-action@v1
  with:
    app: my-app
    image: ${{ env.IMAGE }}
    url: ${{ secrets.VECTOR_DEPLOY_URL }}
    secret: ${{ secrets.VECTOR_HMAC_SECRET }}
```

## Publishing

The canonical source lives at `tooling/vector-deploy-action/action.yml` in the
[`filipnikolov1/launchpad`](https://github.com/filipnikolov1/launchpad) repo.
To make the `uses: filipnikolov1/vector-deploy-action@v1` reference resolve in
users' workflows, the contents of this directory must be mirrored to a public
repo at `filipnikolov1/vector-deploy-action` and tagged `v1`.

Generated Vector setup templates emit `uses: filipnikolov1/vector-deploy-action@v1`
and will fail in users' CI until that repo exists and is tagged.
