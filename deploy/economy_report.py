"""Read aggregate economy pacing from the running network's authenticated admin API."""
import argparse
import json
from manage import Admin, ROOT


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--url', default='http://127.0.0.1:18080')
    args = parser.parse_args()
    print(json.dumps(Admin(args.url, ROOT / 'deploy/secrets/admin').call('/economy'), indent=2))


if __name__ == '__main__':
    main()
