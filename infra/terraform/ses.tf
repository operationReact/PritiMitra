resource "aws_ses_domain_identity" "mail" {
  domain = var.mail_subdomain
}

resource "aws_ses_domain_dkim" "mail" {
  domain = aws_ses_domain_identity.mail.domain
}

resource "aws_ses_domain_mail_from" "mail" {
  domain           = aws_ses_domain_identity.mail.domain
  mail_from_domain = var.mail_from_subdomain
}

locals {
  dns_enabled = var.manage_dns_records && var.route53_zone_id != ""
}

resource "aws_route53_record" "ses_verification" {
  count = local.dns_enabled ? 1 : 0

  zone_id = var.route53_zone_id
  name    = "_amazonses.${var.mail_subdomain}"
  type    = "TXT"
  ttl     = 600
  records = [aws_ses_domain_identity.mail.verification_token]
}

resource "aws_route53_record" "ses_dkim" {
  count = local.dns_enabled ? 3 : 0

  zone_id = var.route53_zone_id
  name    = "${aws_ses_domain_dkim.mail.dkim_tokens[count.index]}._domainkey.${var.mail_subdomain}"
  type    = "CNAME"
  ttl     = 600
  records = ["${aws_ses_domain_dkim.mail.dkim_tokens[count.index]}.dkim.amazonses.com"]
}

resource "aws_route53_record" "ses_mail_from_mx" {
  count = local.dns_enabled ? 1 : 0

  zone_id = var.route53_zone_id
  name    = var.mail_from_subdomain
  type    = "MX"
  ttl     = 600
  records = ["10 feedback-smtp.${var.aws_region}.amazonses.com"]
}

resource "aws_route53_record" "ses_mail_from_spf" {
  count = local.dns_enabled ? 1 : 0

  zone_id = var.route53_zone_id
  name    = var.mail_from_subdomain
  type    = "TXT"
  ttl     = 600
  records = ["v=spf1 include:amazonses.com -all"]
}
