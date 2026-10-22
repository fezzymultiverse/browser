# Festers Browser wiki pages

These files are the source for the project's GitLab wiki. A GitLab wiki is its own git repository, so these are not shown until you push them there.

## Push them to the wiki
```bash
# from the project root, with the project on GitLab
git clone https://gitlab.com/s-o-i-group/browser.wiki.git /tmp/browser.wiki
cp wiki/*.md /tmp/browser.wiki/
rm /tmp/browser.wiki/README.md
cd /tmp/browser.wiki
git add . && git commit -m "Add wiki pages" && git push
```
If the clone fails, open the project's **Plan > Wiki** (or **Wiki**) in GitLab and click **Create your first page** once, then retry.

Page links like `[[Features]]` resolve by title. `Home.md` is the front page and `_sidebar.md` is the sidebar.
