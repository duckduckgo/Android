# frozen_string_literal: true

require 'faraday'

# Main FaradayMiddleware module.
module FaradayMiddleware
  autoload :OAuth,           'faraday_middleware/request/oauth'
  autoload :OAuth2,          'faraday_middleware/request/oauth2'
  autoload :EncodeJson,      'faraday_middleware/request/encode_json'
  autoload :MethodOverride,  'faraday_middleware/request/method_override'
  autoload :Mashify,         'faraday_middleware/response/mashify'
  autoload :Rashify,         'faraday_middleware/response/rashify'
  autoload :ParseJson,       'faraday_middleware/response/parse_json'
  autoload :ParseXml,        'faraday_middleware/response/parse_xml'
  autoload :ParseMarshal,    'faraday_middleware/response/parse_marshal'
  autoload :ParseYaml,       'faraday_middleware/response/parse_yaml'
  autoload :ParseDates,      'faraday_middleware/response/parse_dates'
  autoload :Caching,         'faraday_middleware/response/caching'
  autoload :Chunked,         'faraday_middleware/response/chunked'
  autoload :RackCompatible,  'faraday_middleware/rack_compatible'
  autoload :RedirectLimitReached, 'faraday_middleware/redirect_limit_reached'
  autoload :FollowRedirects, 'faraday_middleware/response/follow_redirects'
  autoload :Instrumentation, 'faraday_middleware/instrumentation'
  autoload :Gzip, 'faraday_middleware/gzip'

  if Faraday::Middleware.respond_to? :register_middleware
    Faraday::Request.register_middleware \
      oauth: -> { OAuth },
      oauth2: -> { OAuth2 },
      json: -> { EncodeJson },
      method_override: -> { MethodOverride }

    Faraday::Response.register_middleware \
      mashify: -> { Mashify },
      rashify: -> { Rashify },
      json: -> { ParseJson },
      json_fix: -> { ParseJson::MimeTypeFix },
      xml: -> { ParseXml },
      marshal: -> { ParseMarshal },
      yaml: -> { ParseYaml },
      dates: -> { ParseDates },
      caching: -> { Caching },
      follow_redirects: -> { FollowRedirects },
      chunked: -> { Chunked }

    Faraday::Middleware.register_middleware \
      instrumentation: -> { Instrumentation },
      gzip: -> { Gzip }
  end
end

require 'faraday_middleware/backwards_compatibility'
