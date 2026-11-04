# Scope

Fix TV Home artwork and its solid tint panel; show the newest 20 Home titles. Preserve the phone layout, resume and history. Unmatched titles retain Telegram artwork; failed matched artwork has the existing generated-cover fallback. TV remote controls and positions stay unchanged. Other targets retain their Home row limit.

# Progress / handoff

Implemented TV-only source selection and tint removal, acceptance of small online posters, cached poster reuse, and a TG Stream Recently added limit of 20. Focused CI: HomeRowsTest, HomeContentTest, TvHomeArtworkTest and existing FeaturedCarouselRenderTest phone layouts. Signed build uses the existing release workflow for 1.0.12. CI and real-device results are pending.
